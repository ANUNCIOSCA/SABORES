# Agenda WA 1.7

Agenda inteligente para Android: recordatorios con alarma, WhatsApp programado, notas inteligentes, clientes y finanzas, copias de seguridad y **Mis Gastos** (control de gastos e ingresos).

## Qué incluye

**De la versión 1.6.0 (se conserva todo):**
- Hoy, Próximos, Notas, Calendario y WhatsApp.
- Recordatorios por voz ("mañana a las 9 enviar un WhatsApp a Carlos").
- Alarma a pantalla completa con aviso 5 minutos antes, posponer 5 min, apagar y eliminar.
- WhatsApp programado con selector de contactos.
- Notas que se clasifican y etiquetan solas (Idea, Negocio, Proyecto, Referencia, Puntos con alguien).
- Clientes y Finanzas: cuentas por cobrar y por pagar, abonos, vencidos, recordatorios e importación desde Excel, CSV o JSON.
- Copia de seguridad en JSON (restaurable), PDF y Markdown. Es compatible con las copias de la 1.6.0.
- Compartir texto desde otras apps hacia Agenda WA.

**Nuevo: Mis Gastos**
- **Inicio:** selector de mes y año con totales de Gastos, Ingresos y Saldo, barra de presupuesto y movimientos agrupados por día.
- **➕:** teclado numérico con suma y resta, más de 20 categorías con íconos, nota, fecha y dictado por voz ("gasté 25 en comida").
- **Gráficos:** dona por categoría (por mes o por año), porcentajes y barras de los últimos 6 meses.
- **Informes:** presupuesto mensual, balance mensual y anual con filtro Todo / años / Otro, saldo inicial, comparación con el mes anterior y exportación a CSV (Excel) y PDF.
- **Yo:** moneda ($, Bs, €, COP…), categorías personalizables, presupuesto, saldo inicial, recordatorio diario y respaldo.
- **Integraciones:**
  - Al marcar un cobro o pago como pagado en Finanzas, se puede pasar a Mis Gastos con un toque.
  - En Hoy aparece el resumen del mes.
  - La búsqueda y el calendario incluyen los totales por día.

## Compilar

### Opción A: GitHub Actions (sin instalar nada)
1. Sube este proyecto a un repositorio privado.
2. Cada `push` a `main` compila el APK en la pestaña **Actions**. Lo descargas en *Artifacts*.
3. Para que las actualizaciones se instalen encima sin desinstalar, agrega estos secretos en *Settings → Secrets → Actions*: `AGENDAWA_KEYSTORE_B64`, `AGENDAWA_KEYSTORE_PASSWORD`, `AGENDAWA_KEY_ALIAS` y `AGENDAWA_KEY_PASSWORD`.

### Opción B: Android Studio
Abre la carpeta y luego *Build → Build APK(s)*. Requiere JDK 17 y Android SDK 35.

## Pasar tus datos desde la versión 1.6.0
La nueva versión está firmada con otra llave, así que Android no la deja instalar encima de la anterior:
1. En la versión vieja: **Más opciones → Copia de seguridad → Guardar copia completa (JSON)**.
2. Desinstala la versión vieja e instala la nueva.
3. En la nueva: **Copia de seguridad → Restaurar desde archivo**.
