-- attendance -> fines
alter table attendance
drop constraint if exists attendance_fine_id_fkey;

alter table attendance
    add constraint attendance_fine_id_fkey
        foreign key (fine_id) references fines(id)
            on delete set null;

-- fine_suggestions -> fines
alter table fine_suggestions
drop constraint if exists fine_suggestions_accepted_fine_id_fkey;

alter table fine_suggestions
    add constraint fine_suggestions_accepted_fine_id_fkey
        foreign key (accepted_fine_id) references fines(id)
            on delete set null;

-- fines -> fine_suggestions
alter table fines
drop constraint if exists fines_accepted_from_suggestion_id_fkey;

alter table fines
    add constraint fines_accepted_from_suggestion_id_fkey
        foreign key (accepted_from_suggestion_id) references fine_suggestions(id)
            on delete set null;
