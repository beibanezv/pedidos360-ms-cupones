# pedidos360-ms-cupones

Microservicio de cupones de descuento (EP3/EP4). Cuenta canjes vía `orden.registrada` y desactiva campañas vencidas con scheduler.

- Puerto 8088. Sin Spring Security (protege el authorizer del Gateway en AWS).
- `POST /cupones` · `GET /cupones/{codigo}` · `GET /cupones/{codigo}/validar`
- Consume `p360.cupones.queue` (binding `orden.registrada` al topic `p360.eventos`).
