create table if not exists account (
    id bigint primary key,
    owner varchar(64) not null,
    balance decimal(19, 2) not null
);

create table if not exists audit_log (
    id bigint auto_increment primary key,
    message varchar(255) not null
);
