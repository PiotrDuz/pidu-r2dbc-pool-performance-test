CREATE TABLE items (
  id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name varchar(255) NOT NULL
);

do $$
begin
for r in 1..100000 loop
insert into items (name) values(r::varchar(255));
end loop;
end;
$$;
