PixelDone v2.5.4 is a local debug bugfix build for the exact-current todo deadline edge case.

Highlights:
- Treats a one-shot todo due at the exact current moment as already overdue.
- Keeps exact-current one-shot todos unscheduled, so they do not request reminder permissions or fire a newly created reminder.
- Refreshes the visible todo-list clock when list deadlines change, making newly added current or past deadlines show the overdue color immediately.
- Aligns DDL countdown text with the overdue color rule by showing `DDL OVERDUE 0D 00H 00M` at the exact due moment.
- Preserves the 2.5.3 architecture, reminder dispatch rules, storage format, image behavior, and in-app update behavior.

Install note: use `PixelDone-2.5.4-debug.apk` for local debug validation. This is not a formal signed release.

CODEX & XUE
