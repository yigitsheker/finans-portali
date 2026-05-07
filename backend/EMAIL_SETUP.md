# Email Notification Setup

## Overview
The application now supports email notifications for price alerts. When an alert is triggered, the user will receive an email with details about the alert.

## Configuration

### 1. Email Settings (application.yml)

The email configuration is in `src/main/resources/application.yml`:

```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: ${MAIL_USERNAME:your-email@gmail.com}
    password: ${MAIL_PASSWORD:your-app-password}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
            required: true
          connectiontimeout: 5000
          timeout: 5000
          writetimeout: 5000
    default-encoding: UTF-8
```

### 2. Environment Variables

Set these environment variables before running the application:

- `MAIL_USERNAME`: Your email address (e.g., `noreply@finansportali.com`)
- `MAIL_PASSWORD`: Your email password or app-specific password

### 3. Gmail Setup (if using Gmail)

If you're using Gmail, you need to:

1. Enable 2-Factor Authentication on your Google account
2. Generate an App Password:
   - Go to https://myaccount.google.com/apppasswords
   - Select "Mail" and your device
   - Copy the generated 16-character password
   - Use this as `MAIL_PASSWORD`

### 4. Other Email Providers

For other email providers, update the `host` and `port` in `application.yml`:

**Outlook/Hotmail:**
```yaml
host: smtp-mail.outlook.com
port: 587
```

**Yahoo:**
```yaml
host: smtp.mail.yahoo.com
port: 587
```

**Custom SMTP:**
```yaml
host: your-smtp-server.com
port: 587  # or 465 for SSL
```

## Email Features

### Price Alert Email

When a price alert is triggered, the user receives an email containing:

- **Alert Symbol and Name**: The instrument that triggered the alert
- **Alert Type**: Price Above, Price Below, % Gain, or % Loss
- **Target Price**: The price level that was set as the target
- **Current Price**: The current price that triggered the alert
- **Creation Price**: The price when the alert was created (if applicable)
- **User Note**: The optional note the user added when creating the alert

### Email Template

The email is sent as HTML with:
- Professional styling with green theme (#22c55e)
- Clear alert information in a structured format
- User's note highlighted in a special box (if provided)
- Responsive design for mobile devices

## User Email Retrieval

Currently, the `KeycloakUserService` is a placeholder. To enable actual email sending, you need to implement one of these approaches:

### Option 1: Extract from JWT Token (Recommended)
Add email claim to JWT token and extract it during authentication.

### Option 2: Store in Database
Store user email in your database during first login/registration.

### Option 3: Keycloak Admin API
Use Keycloak Admin API to fetch user details:

```java
// Example implementation
public String getUserEmail(String userId) {
    String adminToken = getAdminToken();
    String url = keycloakBaseUrl + "/admin/realms/finans/users/" + userId;
    
    WebClient.ResponseSpec response = webClient.get()
        .uri(url)
        .header("Authorization", "Bearer " + adminToken)
        .retrieve();
        
    Map<String, Object> user = response.bodyToMono(Map.class).block();
    return (String) user.get("email");
}
```

## Testing

### Test Email Sending

1. Set environment variables:
```bash
export MAIL_USERNAME=your-email@gmail.com
export MAIL_PASSWORD=your-app-password
```

2. Start the application

3. Create a price alert that will trigger immediately

4. Use the test endpoint (development only):
```bash
curl -X POST http://localhost:8080/api/v1/alerts/test-check
```

### Troubleshooting

**Email not sending:**
- Check environment variables are set correctly
- Verify SMTP credentials
- Check firewall/network settings
- Review application logs for errors

**Gmail "Less secure app" error:**
- Use App Password instead of regular password
- Enable 2-Factor Authentication first

**Connection timeout:**
- Check if port 587 is blocked by firewall
- Try port 465 with SSL instead

## Production Considerations

1. **Use a dedicated email service** like SendGrid, AWS SES, or Mailgun for better deliverability
2. **Implement rate limiting** to prevent email spam
3. **Add email templates** for different notification types
4. **Store email preferences** in database (allow users to opt-out)
5. **Implement retry logic** for failed email sends
6. **Add email queue** for better performance (e.g., using RabbitMQ or Redis)

## Security Notes

- Never commit email credentials to version control
- Use environment variables or secret management systems
- Rotate email passwords regularly
- Monitor email sending for abuse
- Implement CAPTCHA for alert creation if needed
