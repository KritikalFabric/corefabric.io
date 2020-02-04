package org.kritikal.fabric.core;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kritikal.fabric.net.smtp.Rfc822PlainReplyFormats;
import org.kritikal.platform.MyVertxUnitRunner;

@RunWith(MyVertxUnitRunner.class)
public class NR001_Rfc822_Test {

    @Test
    public void nr001_001_apple_mail() {
        Assert.assertEquals(
                "Test mail",
                Rfc822PlainReplyFormats.scrub("Test mail\n\n> quoted text\n> second line\n")
                );
    }

    @Test
    public void nr001_002_outlook_desktop() {
        Assert.assertEquals(
                "Test mail",
                Rfc822PlainReplyFormats.scrub("Test mail\n\n\ufeffQuoted text starts here in desktop outlook\n")
                );
    }

    @Test
    public void nr001_003_outlook_mobile() {
        Assert.assertEquals(
                "Test mail",
                Rfc822PlainReplyFormats.scrub("Test mail\n\n____\nQuoted text starts here in mobile outlook\n")
                );
    }

    @Test
    public void nr001_004_thunderbird_desktop() {
        Assert.assertEquals(
                "reply from thunderbird/yahoo\n\ndid it work?",
                Rfc822PlainReplyFormats.scrub("reply from thunderbird/yahoo\n" +
                        "\n" +
                        "did it work?\n" +
                        "\n" +
                        "On 03/02/2020 19:47, street-stall.space - planet ferenginar wrote:\n" +
                        "> First reply Yahoo mail mobile\n" +
                        ">\n" +
                        "> Sent from Yahoo Mail on Android\n" +
                        ">   \n" +
                        ">    On Mon, 3 Feb 2020 at 20:46, street-stall.space - planet ferenginar<please-reply@ferenginar.street-stall.space> wrote:   First reply sysop\n" +
                        ">")
        );
    }

    @Test
    public void nr001_005_yahoo_mobile() {
        Assert.assertEquals(
                "First reply Yahoo mail mobile",
                Rfc822PlainReplyFormats.scrub("First reply Yahoo mail mobile\n" +
                        "\n" +
                        "Sent from Yahoo Mail on Android\n" +
                        "\n" +
                        "    On Mon, 3 Feb 2020 at 20:46, street-stall.space - planet ferenginar\n" +
                        "    <please-reply@ferenginar.street-stall.space> wrote:\n" +
                        "    First reply sysop\n" +
                        "\n" +
                        "    -- \n" +
                        "    sysop bbs    street-stall.space - planet ferenginar\n" +
                        "    https://ferenginar.street-stall.space/-/bbs/post/c1217e83-6c2e-4387-a036-0c38fc1f3106/ac4af582-dea8-4ddf-8439-4675b29389d4\n")
        );
    }

    @Test
    public void nr001_006_outlook_mobile() {
        Assert.assertEquals(
                "Some tickets never close",
                Rfc822PlainReplyFormats.scrub(
                "Some tickets never close\n" +
                       "\n" +
                       "Get Outlook for Android<https://aka.ms/ghei36>\n" +
                       "\n" +
                       "________________________________\n" +
                       "From: street-stall.space - planet ferenginar <please-reply@ferenginar.street-stall.space>\n" +
                       "Sent: Monday, February 3, 2020 8:06:19 PM\n" +
                       "To: street-stall-sysop@hotmail.com <street-stall-sysop@hotmail.com>\n" +
                       "Subject: Re: test post please reply from outlook eventually\n" +
                       "\n" +
                       "Yes please close the ticket!"
                )
        );
    }

    @Test
    public void nr001_007_thunderbird_desktop_forward() {
        Assert.assertEquals(
                "Your response",
                Rfc822PlainReplyFormats.scrub("Your response\n" +
                        "\n" +
                        "\n" +
                        "-------- Forwarded Message --------\n" +
                        "Date: \tMon, 3 Feb 2020 19:47:17 +0000 (UTC)\n" +
                        "From: \tBen Gould <street_stall_sysop@yahoo.com>\n" +
                        "Reply-To: \tstreet_stall_sysop@yahoo.com <street_stall_sysop@yahoo.com>\n" +
                        "To: \treply.ac4af582-dea8-4ddf-8439-4675b29389d4.bbs-post@ferenginar.street-stall.space <reply.ac4af582-dea8-4ddf-8439-4675b29389d4.bbs-post@ferenginar.street-stall.space>\n" +
                        "Message-ID: \t<1208689595.1957749.1580759237959@mail.yahoo.com>\n" +
                        "In-Reply-To: \t<5aa490ef-a265-4abc-b77f-adde549079d1@ferenginar.street-stall.space>\n" +
                        "References: \t<5aa490ef-a265-4abc-b77f-adde549079d1@ferenginar.street-stall.space>\n" +
                        "Subject: \tRe: Test Yahoo mail\n" +
                        "MIME-Version: \t1.0\n" +
                        "Content-Type: \tmultipart/alternative; boundary=\"----=_Part_1957748_482627109.1580759237957\"\n" +
                        "X-Mailer: \tWebService/1.1.15149 YahooMailAndroidMobile YMobile/1.0 (com.yahoo.mobile.client.android.mail/6.2.4; Android/9; HUAWEIANE-L21; HWANE; HUAWEI; ANE-LX1; 5.37; 2060x1080;)\n" +
                        "Content-Length: \t1999\n" +
                        "\n" +
                        "\n" +
                        "First reply Yahoo mail mobile\n" +
                        "\n" +
                        "Sent from Yahoo Mail on Android\n" +
                        "\n" +
                        "    On Mon, 3 Feb 2020 at 20:46, street-stall.space - planet ferenginar\n" +
                        "    <please-reply@ferenginar.street-stall.space> wrote:\n" +
                        "    First reply sysop\n" +
                        "\n" +
                        "    -- \n" +
                        "    sysop bbs    street-stall.space - planet ferenginar\n" +
                        "    https://ferenginar.street-stall.space/-/bbs/post/c1217e83-6c2e-4387-a036-0c38fc1f3106/ac4af582-dea8-4ddf-8439-4675b29389d4\n" +
                        "\n")
        );
    }

    @Test
    public void nr001_008_apple_mail_reply() {
        Assert.assertEquals(
                "Reply apple mail",
                Rfc822PlainReplyFormats.scrub("Reply apple mail\n" +
                        "\n" +
                        "> On 4 Feb 2020, at 10:30, street-stall.space - planet ferenginar =\n" +
                        "<please-reply@ferenginar.street-stall.space> wrote:\n" +
                        ">=20\n" +
                        "> Reply desktop outlook\n" +
                        ">=20\n" +
                        "> --=20\n" +
                        "> sysop bbs\tstreet-stall.space - planet ferenginar\n" +
                        "> =\n" +
                        "https://ferenginar.street-stall.space/-/bbs/post/c1217e83-6c2e-4387-a036-0=\n" +
                        "c38fc1f3106/1021c4c8-9e4e-4123-95a1-2c4a7ab9443f\n" +
                        "\n" +
                        "--\n" +
                        "System Operator\n" +
                        "sysop@street-stall.space\n" +
                        "+44 (0)330 808 2056")
        );
    }


}
