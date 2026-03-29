package com.example.distributedtxdemo.tcc;

import com.example.distributedtxdemo.common.WalletAccountRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TccDemoService {

    private final JdbcTemplate jdbcTemplate;
    private final WalletAccountRepository walletAccountRepository;

    public TccDemoService(JdbcTemplate jdbcTemplate, WalletAccountRepository walletAccountRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.walletAccountRepository = walletAccountRepository;
    }

    @Transactional
    public TccStepResult tryReserve(String bizNo, BigDecimal amount) {
        Reservation reservation = findReservation(bizNo);
        if (reservation != null) {
            return switch (reservation.status()) {
                case "TRY", "CONFIRMED" -> new TccStepResult(reservation.status(), true, "try idempotent or already confirmed");
                case "CANCELED" -> new TccStepResult("BLOCKED", false, "try blocked by previous cancel, used for anti-hanging");
                default -> new TccStepResult(reservation.status(), false, "unexpected status");
            };
        }

        WalletAccountRepository.WalletAccount account = walletAccountRepository.findByAccountNo("ALICE");
        if (account == null || account.availableAmount().compareTo(amount) < 0) {
            throw new IllegalStateException("available balance not enough for TCC try");
        }

        walletAccountRepository.addFrozen("ALICE", amount);
        jdbcTemplate.update(
                "insert into tcc_reservation(biz_no, status, amount, note, created_at, updated_at) values (?, 'TRY', ?, ?, ?, ?)",
                bizNo,
                amount,
                "try-reserved",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        return new TccStepResult("TRY", true, "amount frozen");
    }

    @Transactional
    public TccStepResult confirm(String bizNo) {
        Reservation reservation = findReservation(bizNo);
        if (reservation == null) {
            return new TccStepResult("EMPTY_CONFIRM", true, "confirm before try, nothing to do");
        }
        if ("CONFIRMED".equals(reservation.status())) {
            return new TccStepResult("CONFIRMED", true, "confirm idempotent");
        }
        if ("CANCELED".equals(reservation.status())) {
            return new TccStepResult("CANCELED", false, "cannot confirm after cancel");
        }

        walletAccountRepository.commitFrozen("ALICE", reservation.amount());
        jdbcTemplate.update(
                "update tcc_reservation set status = 'CONFIRMED', note = ?, updated_at = ? where biz_no = ?",
                "confirm-finished",
                LocalDateTime.now(),
                bizNo
        );
        return new TccStepResult("CONFIRMED", true, "balance deducted from frozen amount");
    }

    @Transactional
    public TccStepResult cancel(String bizNo) {
        Reservation reservation = findReservation(bizNo);
        if (reservation == null) {
            jdbcTemplate.update(
                    "insert into tcc_reservation(biz_no, status, amount, note, created_at, updated_at) values (?, 'CANCELED', 0, ?, ?, ?)",
                    bizNo,
                    "empty-cancel",
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );
            return new TccStepResult("EMPTY_CANCEL", true, "cancel arrives before try, create tombstone to prevent hanging");
        }
        if ("CANCELED".equals(reservation.status())) {
            return new TccStepResult("CANCELED", true, "cancel idempotent");
        }
        if ("CONFIRMED".equals(reservation.status())) {
            return new TccStepResult("CONFIRMED", false, "confirm already happened, cancel ignored");
        }

        walletAccountRepository.releaseFrozen("ALICE", reservation.amount());
        jdbcTemplate.update(
                "update tcc_reservation set status = 'CANCELED', note = ?, updated_at = ? where biz_no = ?",
                "cancel-finished",
                LocalDateTime.now(),
                bizNo
        );
        return new TccStepResult("CANCELED", true, "frozen amount released");
    }

    public TccFlowResult successfulConfirmDemo(String bizNo, BigDecimal amount) {
        List<String> steps = new ArrayList<>();
        WalletAccountRepository.WalletAccount before = walletAccountRepository.findByAccountNo("ALICE");
        steps.add("before -> balance=" + before.balance() + ", frozen=" + before.frozenAmount());
        steps.add("try -> " + tryReserve(bizNo, amount).message());
        steps.add("confirm -> " + confirm(bizNo).message());

        WalletAccountRepository.WalletAccount after = walletAccountRepository.findByAccountNo("ALICE");
        steps.add("after -> balance=" + after.balance() + ", frozen=" + after.frozenAmount());
        return new TccFlowResult(bizNo, reservationStatus(bizNo), steps);
    }

    public TccFlowResult cancelAndEmptyCancelDemo(String emptyCancelBizNo, String normalBizNo, BigDecimal amount) {
        List<String> steps = new ArrayList<>();
        steps.add("empty cancel -> " + cancel(emptyCancelBizNo).message());
        steps.add("late try after empty cancel -> " + tryReserve(emptyCancelBizNo, amount).message());
        steps.add("normal try -> " + tryReserve(normalBizNo, amount).message());
        steps.add("normal cancel -> " + cancel(normalBizNo).message());
        return new TccFlowResult(normalBizNo, reservationStatus(normalBizNo), steps);
    }

    public String reservationStatus(String bizNo) {
        Reservation reservation = findReservation(bizNo);
        return reservation == null ? "NONE" : reservation.status();
    }

    private Reservation findReservation(String bizNo) {
        try {
            return jdbcTemplate.queryForObject(
                    "select biz_no, status, amount from tcc_reservation where biz_no = ?",
                    (rs, rowNum) -> new Reservation(
                            rs.getString("biz_no"),
                            rs.getString("status"),
                            rs.getBigDecimal("amount")
                    ),
                    bizNo
            );
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private record Reservation(String bizNo, String status, BigDecimal amount) {
    }

    public record TccStepResult(String status, boolean success, String message) {
    }

    public record TccFlowResult(String bizNo, String finalReservationStatus, List<String> steps) {
    }
}
