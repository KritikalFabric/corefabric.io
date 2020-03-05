package org.kritikal.fabric.net.smtp;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Rfc822PlainReplyFormats {

    private static Pattern reOnWrote = Pattern.compile("^\\s*on\\s+(.*|.*\\r?\\n.*|.*\\r?\\n.*\\r?\\n.*)\\s+wrote:\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static Pattern reVendors = Pattern.compile("^\\s*(Get Outlook for|Sent from (Yahoo|my iPhone))", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    public static String scrub(String emailBody) {
        return stripSignature2(
            stripSignature1(
                stripQuotedContent(emailBody).trim()
            ).trim()
        ).trim();
    }

    private static String stripQuotedContent(String emailBody) {
        int quote = emailBody.indexOf("\n> ");
        int outlook = emailBody.indexOf('\ufeff', 4);
        int lookout = emailBody.indexOf("\n____");
        int ascii = emailBody.indexOf("\n----");

        if (quote > -1 && outlook > 0 && quote > outlook - 1) {
            quote = outlook - 1;
        }
        if (quote > -1) {
            return emailBody.substring(0, quote + 1);
        } else if (outlook > -1) {
            return emailBody.substring(0, outlook);
        } else if (lookout > -1) {
            return emailBody.substring(0, lookout + 1);
        } else if (ascii > -1) {
            return emailBody.substring(0, ascii + 1);
        }
        return emailBody;
    }

    private static String stripSignature1(String emailBody) {
        int signature = emailBody.indexOf("\n--");
        if (signature > -1) {
            return emailBody.substring(0, signature+1);
        }
        return emailBody;
    }

    private static String stripSignature2(String emailBody) {
        Matcher mOnWrote = reOnWrote.matcher(emailBody);
        boolean foundOnWrote = mOnWrote.find();
        Matcher mVendors = reVendors.matcher(emailBody);
        boolean foundVendors = mVendors.find();
        if (foundOnWrote && foundVendors) {
            int i = Math.min(mOnWrote.start(), mVendors.start());
            return emailBody.substring(0, i);
        } else if (foundOnWrote) {
            return emailBody.substring(0, mOnWrote.start());
        } else if (foundVendors) {
            return emailBody.substring(0, mVendors.start());
        }
        return emailBody;
    }

}
