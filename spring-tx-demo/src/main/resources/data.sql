insert into account (id, owner, balance)
select 1, 'Alice', 1000.00
where not exists (select 1 from account where id = 1);

insert into account (id, owner, balance)
select 2, 'Bob', 1000.00
where not exists (select 1 from account where id = 2);
