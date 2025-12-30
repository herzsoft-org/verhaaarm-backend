alter table attendance
drop constraint if exists attendance_event_id_fkey;

alter table attendance
    add constraint attendance_event_id_fkey
        foreign key (event_id) references events(id)
            on delete cascade;
