package org.kritikal.fabric.net.smtp;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ben on 5/15/14.
 */
public class SmtpDeliveryInformation {

    public boolean useMx;
    public String smartHost;
    public int smartPort;
    public boolean skipBlacklist = false;

    public boolean haveLookedUpMxList;
    public List<String> remainingMxListForDns;

    public boolean readyForConnect;
    public List<String> remainingIpAddressesForConnect;

    public SmtpDeliveryInformation(JsonObject o)
    {
        String smartHost = o.getString("smarthost");
        if (smartHost != null && !"".equals(smartHost))
        {
            useMx = false;
            this.smartHost = smartHost;

            smartPort = o.getInteger("smartport");
            if (smartPort <= 0 || smartPort > 65535)
                smartPort = 25;

            readyForConnect = true;
        }
        else
        {
            useMx = true;
            this.smartPort = 25;

            haveLookedUpMxList = o.getBoolean("have_looked_up_mx") == Boolean.TRUE;
            remainingMxListForDns = new ArrayList<String>();
            JsonArray ary = o.getJsonArray("remaining_mx_list_for_dns");
            if (ary != null)
                for (int i = 0; i < ary.size(); ++i) remainingMxListForDns.add(ary.getString(i));

            readyForConnect = o.getBoolean("ready_for_connect") == Boolean.TRUE;
            remainingIpAddressesForConnect = new ArrayList<String>();
            ary = o.getJsonArray("remaining_ip_addresses_for_connect");
            if (ary != null)
                for (int i = 0; i < ary.size(); ++i) remainingIpAddressesForConnect.add(ary.getString(i));
        }
    }

    public void toJsonObject(JsonObject o)
    {
        if (useMx)
        {
            o.put("have_looked_up_mx", haveLookedUpMxList);
            JsonArray ary = new JsonArray();
            for (int i = 0; i < remainingMxListForDns.size(); ++i)
            {
                ary.add(remainingMxListForDns.get(i));
            }
            o.put("remaining_mx_list_for_dns", ary);

            o.put("ready_for_connect", readyForConnect);
            ary = new JsonArray();
            for (int i = 0; i < remainingIpAddressesForConnect.size(); ++i)
            {
                ary.add(remainingIpAddressesForConnect.get(i));
            }
            o.put("remaining_ip_addresses_for_connect", ary);
        }
        else
        {
            o.put("smarthost", smartHost);
            o.put("smartport", smartPort);
        }
    }

    public void toMiniJsonObject(JsonObject o)
    {
        if (!useMx)
        {
            o.put("smarthost", smartHost);
            o.put("smartport", smartPort);
        }
    }

    public String mxName()
    {
        if (useMx)
        {
            if (remainingMxListForDns.size() == 0) return null;

            String name = remainingMxListForDns.get(0);
            remainingMxListForDns.remove(0);
            return name;
        }
        else
        {
            return null;
        }
    }

    public String connectAddress()
    {
        if (useMx)
        {
            if (remainingIpAddressesForConnect.size() == 0) return null;

            String address = remainingIpAddressesForConnect.get(0);
            remainingIpAddressesForConnect.remove(0);
            return address;
        }
        else
        {
            return smartHost;
        }
    }
}
