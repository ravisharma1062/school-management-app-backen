-- User accounts with role TEACHER were being created without a corresponding row in
-- teachers, since UserService.create() never inserted one. This left every such teacher
-- unassignable to a timetable entry (and unresolvable by any other Teacher-based lookup).
-- Backfill a teachers row for every existing TEACHER user that is missing one.
INSERT INTO teachers (id, user_id, subjects, classes_assigned)
SELECT gen_random_uuid(), u.id, NULL, NULL
FROM users u
WHERE u.role = 'TEACHER'
  AND NOT EXISTS (SELECT 1 FROM teachers t WHERE t.user_id = u.id);
