package com.example.orderobservabilitypatterndemo.pattern;

import com.example.orderobservabilitypatterndemo.treasury.TreasuryFlowDemoService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DesignPatternDemoService {

    private final TreasuryFlowDemoService treasuryFlowDemoService;

    public DesignPatternDemoService(TreasuryFlowDemoService treasuryFlowDemoService) {
        this.treasuryFlowDemoService = treasuryFlowDemoService;
    }

    public PatternSummary patternSummaryDemo() {
        TreasuryFlowDemoService.TreasuryFlowResult result =
                treasuryFlowDemoService.treasuryFlowDemo("TXN-PATTERN-01", "DIRECT_BANK", 120, 300);

        return new PatternSummary(
                result.templateName(),
                result.strategyName(),
                result.validatorNames()
        );
    }

    public static final class PatternSummary {

        private final String templateName;
        private final String strategyName;
        private final List<String> validatorNames;

        public PatternSummary(String templateName, String strategyName, List<String> validatorNames) {
            this.templateName = templateName;
            this.strategyName = strategyName;
            this.validatorNames = validatorNames;
        }

        public String templateName() {
            return templateName;
        }

        public String strategyName() {
            return strategyName;
        }

        public List<String> validatorNames() {
            return validatorNames;
        }
    }
}
