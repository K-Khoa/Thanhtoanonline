# Thanhtoanonline — Music Store demo

Maven WAR application running on Tomcat 10.1 / Java 17.

## Structure
- `src/main/java/com/example/shop/`: Java servlets and application classes
- `src/main/webapp/`: HTML, CSS and browser JavaScript
- `Dockerfile`, `pom.xml`: repository root

## Render environment variables
- `BREVO_API_KEY`: Brevo API key for transactional email
- `MAIL_FROM`: verified sender email configured in Brevo

The payment page is a simulation and does not charge real money. Orders are stored in memory and are lost when the service restarts.
