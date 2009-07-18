/*
 * ReadTemp modifications for Indigo by Jamus Jegier 
 */

/*---------------------------------------------------------------------------
 * Copyright (C) 1999,2000 Dallas Semiconductor Corporation, All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY,  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL DALLAS SEMICONDUCTOR BE LIABLE FOR ANY CLAIM, DAMAGES
 * OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * Except as contained in this notice, the name of Dallas Semiconductor
 * shall not be used except as stated in the Dallas Semiconductor
 * Branding Policy.
 *---------------------------------------------------------------------------
 */

import com.dalsemi.onewire.*;
import com.dalsemi.onewire.adapter.*;
import com.dalsemi.onewire.container.*;
import java.util.Vector;
import java.io.*;
import com.dalsemi.onewire.utils.CRC16;
import java.util.*;
import org.json.*;


public class ReadTemp
    {
        static HashMap variableNames;
        
        /* Ideally, we'd use the applescript extensions that's available
         in Java 6, but apple didn't port that to the ppc platform */
        /** Executes the given applescript code and returns the first line of the 
         output as a string */
        static String doApplescript(String script)
        {
            String line;
            try {
                // Start applescript 
                Process p=Runtime.getRuntime().exec("/usr/bin/osascript -s o -");
                
                // Send applescript via stdin
                OutputStream stdin=p.getOutputStream();
                stdin.write(script.getBytes());
                stdin.flush();
                stdin.close();
                
                // get first line of output
                BufferedReader input =
                new BufferedReader
                (new InputStreamReader(p.getInputStream()));
                line=input.readLine();
                input.close();
                
                // If we get an exit code, print it out
                if (p.waitFor() != 0) {
                    System.err.println("exit value = " +
                                       p.exitValue());
                    System.err.println(line);
                }
                return line;
            }
            catch (Exception e) {
                System.err.println(e);
            }
            
            return "";
        }
        
        /* Load the configuration from Indigo.  They should be in the JSON format 
         like this: 
         { "one_wire_hex_id" : "variable_name", "one_wire_hex_id2" : "variable_name2"}
         */
        static void populateVariableNames()
        {
            String json=doApplescript("tell application \"IndigoServer\" to return value of variable \"ow_config\"");
            try {
                JSONObject j=new JSONObject(json);
                java.util.Iterator i=j.keys();
                while (i.hasNext() ) {
                    String key=(String)i.next();
                    variableNames.put(key,j.getString(key));
                }
            } catch (Exception e) {
                System.err.println(e);
            }
        }
        
         
        /* Sets an indigo variable through applescript*/ 
        static void setVariable(String id, String value)
        {
            String variable=(String)variableNames.get(id); 
            doApplescript("tell application \"IndigoServer\" to set value of variable \""+variable+"\" to \""+value+"\"");
        }
        
        static int parseInt (BufferedReader in, int def)
        {
            try
            {
                return Integer.parseInt(in.readLine());
            }
            catch (Exception e)
            {
                return def;
            }
        }
        
        /**
         * Method printUsageString
         *
         *
         */
        public static void printUsageString ()
        {
            System.out.println("Temperature Container Demo");
            System.out.println("Usage: ");
            System.out.println("   java ReadTemp ADAPTER_PORT");
            System.out.println(
                               "ADAPTER_PORT is a String that contains the name of the");
            System.out.println(
                               "adapter you would like to use and the port you would like");
            System.out.println("to use, for example: ");
            System.out.println("   java ReadTemp http://rxtx.qbang.org/pub/rxtx/rxtx-2.2pre2-bins.zip\n");
        }
        
        /**
         * Method main
         *
         *
         * @param args
         *
         * @throws OneWireException
         * @throws OneWireIOException
         *
         */
        public static void main (String[] args)
        throws OneWireIOException, OneWireException
        {
            boolean       usedefault   = false;
            DSPortAdapter access       = null;
            String        adapter_name = null;
            String        port_name    = null;
            
            variableNames=new HashMap();
            
            if ((args == null) || (args.length < 1))
            {
                try
                {
                    access = OneWireAccessProvider.getDefaultAdapter();
                    
                    if (access == null)
                        throw new Exception();
                }
                catch (Exception e)
                {
                    System.out.println("Couldn't get default adapter!");
                    printUsageString();
                    
                    return;
                }
                
                usedefault = true;
            }
            
            if (!usedefault)
            {
                StringTokenizer st = new StringTokenizer(args [0], "_");
                
                if (st.countTokens() != 2)
                {
                    printUsageString();
                    
                    return;
                }
                
                adapter_name = st.nextToken();
                port_name    = st.nextToken();
                
                System.out.println("Adapter Name: " + adapter_name);
                System.out.println("Port Name: " + port_name);
            }
            
            if (access == null)
            {
                try
                {
                    access = OneWireAccessProvider.getAdapter(adapter_name,
                                                              port_name);
                }
                catch (Exception e)
                {
                    System.out.println(
                                       "That is not a valid adapter/port combination.");
                    
                    Enumeration en = OneWireAccessProvider.enumerateAllAdapters();
                    
                    while (en.hasMoreElements())
                    {
                        DSPortAdapter temp = ( DSPortAdapter ) en.nextElement();
                        
                        System.out.println("Adapter: " + temp.getAdapterName());
                        
                        Enumeration f = temp.getPortNames();
                        
                        while (f.hasMoreElements())
                        {
                            System.out.println("   Port name : "
                                               + (( String ) f.nextElement()));
                        }
                    }
                    
                    return;
                }
            }
            
            populateVariableNames();
            while(true)
            {
                access.adapterDetected();
                access.targetAllFamilies();
                access.beginExclusive(true);
                access.reset();
                access.setSearchAllDevices();
                
                boolean next = access.findFirstDevice();
                
                if (!next)
                {
                    System.out.println("Could not find any iButtons!");
                    
                    return;
                }
                
                while (next)
                {
                    OneWireContainer owc = access.getDeviceContainer();
                    
                    
                    boolean              isTempContainer = false;
                    TemperatureContainer tc              = null;
                    
                    try
                    {
                        tc              = ( TemperatureContainer ) owc;
                        isTempContainer = true;
                    }
                    catch (Exception e)
                    {
                        tc              = null;
                        isTempContainer = false;   //just to reiterate
                    }
                    
                    if (isTempContainer)
                    {
                        
                        double high  = 0.0;
                        double low   = 0.0;
                        byte[] state = tc.readDevice();
                        
                        boolean selectable = tc.hasSelectableTemperatureResolution();
                        String id=owc.getAddressAsString();
                        
                        if (selectable)
                            try
                        {
                            tc.setTemperatureResolution(0.0625, state);
                        }
                        catch (Exception e)
                        {
                            System.out.println("= Could not set resolution for "+id+": "
                                               + e.toString());
                        }
                        
                        try
                        {
                            tc.writeDevice(state);
                        }
                        catch (Exception e)
                        {
                            System.out.println(
                                               "= Could not write device state, all changes lost.");
                            System.out.println("= Exception occurred for "+id+": " + e.toString());
                        }
                        
                        boolean conversion=false;
                        try
                        {
                            tc.doTemperatureConvert(state);
                            conversion=true;
                        }
                        catch (Exception e)
                        {
                            System.out.println(
                                               "= Could not complete temperature conversion...");
                            System.out.println("= Exception occurred for "+id+": " + e.toString());
                        }
                        
                        if (conversion) 
                        {
                            state = tc.readDevice();
                            
                            double temp = tc.getTemperature(state);
                            if (temp<84) {
                                double temp_f = (9.0/5.0)*temp+32;
                                setVariable(id,Double.toString(temp_f));
                            }
                            System.out.println("= Reported temperature from "+
                                               (String)variableNames.get(id)+"("+
                                               id + "):" + temp);
                        }
                    }
                    next = access.findNextDevice();
                }
                try {
                    Thread.sleep(60*5*1000);
                } catch (Exception e) {}
            }
        }
    }
