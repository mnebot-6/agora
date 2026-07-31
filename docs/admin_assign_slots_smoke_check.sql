-- ============================================================================
-- Admins apuntan gente en los huecos — smoke check
--
-- Bloques 1-5: SELECTs. Pégalos en Supabase Dashboard → SQL Editor DESPUÉS de
-- aplicar las dos migraciones. No escriben nada.
--
-- Bloques 6-13: se prueban DESDE LA APP con un admin logueado. La comprobación
-- de admin depende de auth.uid(), que en el editor SQL es NULL, así que llamar
-- a los RPC desde aquí no prueba nada útil.
-- ============================================================================


-- 1) Los dos RPC nuevos existen
select proname
from pg_proc
where proname in ('admin_assign_slot', 'admin_assign_new_slot')
order by proname;
-- Esperado: 2 filas.


-- 2) La columna y la constraint existen
select column_name, data_type, is_nullable
from information_schema.columns
where table_name = 'slots' and column_name = 'guest_label';
-- Esperado: 1 fila — guest_label, text, YES.

select conname from pg_constraint where conname = 'slots_no_owner_and_label';
-- Esperado: 1 fila.


-- 3) release_slot lleva los cuatro arreglos
select
    position('IS DISTINCT FROM' in prosrc) > 0            as usa_is_distinct_from,
    position('guest_label = NULL' in prosrc) > 0          as limpia_guest_label,
    position('is_guest = false' in prosrc) > 0            as limpia_is_guest,
    position('pending guest slot' in prosrc) > 0          as protege_pending
from pg_proc where proname = 'release_slot';
-- Esperado: true en las cuatro.


-- 4) reject_guest_request también limpia la etiqueta
select position('guest_label = NULL' in prosrc) > 0 as limpia_guest_label
from pg_proc where proname = 'reject_guest_request';
-- Esperado: true.
-- Por qué importa: es el otro sitio que devuelve una plaza a 'available'. Sin
-- esto, la plaza se queda con la etiqueta pegada y viola la constraint nueva en
-- cuanto alguien intente reservarla — y no hay policy de DELETE para limpiarla.


-- 5) slot_assigned está permitido por la constraint de notificaciones
select position('slot_assigned' in pg_get_constraintdef(oid)) > 0 as permite_slot_assigned
from pg_constraint where conname = 'notifications_type_check';
-- Esperado: true. Si es false, CADA notificación de los RPC nuevos falla.


-- ============================================================================
-- Desde la app, con un admin logueado. Tras cada caso, releer la fila con:
--   select id, status, reserved_by, guest_label, is_guest
--   from slots where id = '<slot_id>';
-- ============================================================================
--
--  6) Apuntar a un MIEMBRO en una plaza libre
--     → status 'reserved', reserved_by = ese usuario, guest_label NULL.
--     → al miembro le llega la notificación "Te han apuntado".
--     → si tocas la notificación, abre la actividad (no un tap muerto).
--
--  7) Apuntar un NOMBRE SUELTO
--     → status 'reserved', reserved_by NULL, guest_label con el texto.
--     → la plaza se ve en la app con el nombre y el chip de invitado.
--
--  8) Apuntar sobre una plaza YA OCUPADA (dos móviles, o recarga a medias)
--     → snackbar "Esa plaza ya está ocupada", la fila no cambia.
--
--  9) Apuntar a alguien que YA TIENE plaza en esa actividad
--     → error "already has a slot in this activity".
--
-- 10) Nombre suelto VACÍO o sólo espacios
--     → el botón Apuntar está deshabilitado en la app; si llegara al RPC,
--       "Guest label cannot be empty".
--
-- 11) Liberar la plaza de etiqueta SIENDO ADMIN
--     → status 'available', guest_label NULL, is_guest false.
--
-- 12) Liberar la plaza de etiqueta SIENDO UN MIEMBRO NORMAL
--     → error "Only an admin can release a guest-label slot".
--     Este es el agujero que motivó el arreglo: antes la comparación con "!="
--     daba NULL con reserved_by NULL y el IF no disparaba, así que pasaba.
--
-- 13) Aviso de cola de suplentes
--     Con alguien en la cola, abrir el diálogo de apuntar
--     → aparece "Hay N personas en la cola de suplentes" y el botón dice
--       "Apuntar igualmente". El RPC no bloquea: el admin manda.
