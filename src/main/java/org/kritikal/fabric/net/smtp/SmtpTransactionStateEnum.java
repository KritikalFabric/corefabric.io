package org.kritikal.fabric.net.smtp;

/**
 * Created by ben on 5/14/14.
 */
public enum SmtpTransactionStateEnum {
    INITIAL,
    HELO,
    STARTTLS,
    MAIL_FROM,
    RCPT_TO,
    DATA,
}
