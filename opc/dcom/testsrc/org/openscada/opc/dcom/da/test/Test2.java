/*
 * This file is part of the OpenSCADA project
 * Copyright (C) 2006 inavare GmbH (http://inavare.com)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.openscada.opc.dcom.da.test;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;

import org.jinterop.dcom.common.JIException;
import org.jinterop.dcom.common.JISystem;
import org.jinterop.dcom.core.IJIComObject;
import org.jinterop.dcom.core.JIClsid;
import org.jinterop.dcom.core.JIComServer;
import org.jinterop.dcom.core.JIProgId;
import org.jinterop.dcom.core.JISession;
import org.jinterop.dcom.core.JIVariant;
import org.openscada.opc.dcom.common.EventHandler;
import org.openscada.opc.dcom.common.KeyedResult;
import org.openscada.opc.dcom.common.KeyedResultSet;
import org.openscada.opc.dcom.common.Result;
import org.openscada.opc.dcom.common.ResultSet;
import org.openscada.opc.dcom.common.impl.OPCCommon;
import org.openscada.opc.dcom.da.IORequest;
import org.openscada.opc.dcom.da.OPCBROWSEDIRECTION;
import org.openscada.opc.dcom.da.OPCBROWSETYPE;
import org.openscada.opc.dcom.da.OPCENUMSCOPE;
import org.openscada.opc.dcom.da.OPCGroupState;
import org.openscada.opc.dcom.da.OPCITEMDEF;
import org.openscada.opc.dcom.da.OPCITEMRESULT;
import org.openscada.opc.dcom.da.OPCITEMSOURCE;
import org.openscada.opc.dcom.da.OPCITEMSTATE;
import org.openscada.opc.dcom.da.OPCNAMESPACETYPE;
import org.openscada.opc.dcom.da.OPCSERVERSTATUS;
import org.openscada.opc.dcom.da.PropertyDescription;
import org.openscada.opc.dcom.da.impl.OPCAsyncIO2;
import org.openscada.opc.dcom.da.impl.OPCBrowseServerAddressSpace;
import org.openscada.opc.dcom.da.impl.OPCGroupStateMgt;
import org.openscada.opc.dcom.da.impl.OPCItemIO;
import org.openscada.opc.dcom.da.impl.OPCItemMgt;
import org.openscada.opc.dcom.da.impl.OPCItemProperties;
import org.openscada.opc.dcom.da.impl.OPCServer;
import org.openscada.opc.dcom.da.impl.OPCSyncIO;
import org.openscada.opc.dcom.da.impl.WriteRequest;

public class Test2
{
    private static JISession _session = null;

    public static void showError ( OPCCommon common, int errorCode ) throws JIException
    {
        System.out.println ( String.format ( "Error (%X): '%s'", errorCode, common.getErrorString ( errorCode, 1033 ) ) );
    }

    public static void showError ( OPCServer server, int errorCode ) throws JIException
    {
        showError ( server.getCommon (), errorCode );
    }

    public static boolean dumpOPCITEMRESULT ( KeyedResultSet<OPCITEMDEF, OPCITEMRESULT> result )
    {
        int failed = 0;
        for ( KeyedResult<OPCITEMDEF, OPCITEMRESULT> resultEntry : result )
        {
            System.out.println ( "==================================" );
            System.out.println ( String.format ( "Item: '%s' ", resultEntry.getKey ().getItemID () ) );

            System.out.println ( String.format ( "Error Code: %08x", resultEntry.getErrorCode () ) );
            if ( !resultEntry.isFailed () )
            {
                System.out.println ( String.format ( "Server Handle: %08X", resultEntry.getValue ().getServerHandle () ) );
                System.out.println ( String.format ( "Data Type: %d", resultEntry.getValue ().getCanonicalDataType () ) );
                System.out.println ( String.format ( "Access Rights: %d", resultEntry.getValue ().getAccessRights () ) );
                System.out.println ( String.format ( "Reserved: %d", resultEntry.getValue ().getReserved () ) );
            }
            else
                failed++;
        }
        return failed == 0;
    }

    public static void testItems ( OPCServer server, OPCGroupStateMgt group, String... itemIDs ) throws IllegalArgumentException, UnknownHostException, JIException
    {
        OPCItemMgt itemManagement = group.getItemManagement ();
        List<OPCITEMDEF> items = new ArrayList<OPCITEMDEF> ( itemIDs.length );
        for ( String id : itemIDs )
        {
            OPCITEMDEF item = new OPCITEMDEF ();
            item.setItemID ( id );
            item.setClientHandle ( new Random ().nextInt () );
            items.add ( item );
        }

        OPCITEMDEF[] itemArray = items.toArray ( new OPCITEMDEF[0] );

        System.out.println ( "Validate" );
        KeyedResultSet<OPCITEMDEF, OPCITEMRESULT> result = itemManagement.validate ( itemArray );
        if ( !dumpOPCITEMRESULT ( result ) )
            return;

        // now add them to the group
        System.out.println ( "Add" );
        result = itemManagement.add ( itemArray );
        if ( !dumpOPCITEMRESULT ( result ) )
            return;

        // get the server handle array
        Integer[] serverHandles = new Integer[itemArray.length];
        for ( int i = 0; i < itemArray.length; i++ )
        {
            serverHandles[i] = new Integer ( result.get ( i ).getValue ().getServerHandle () );
        }

        // set them active
        System.out.println ( "Activate" );
        ResultSet<Integer> resultSet = itemManagement.setActiveState ( true, serverHandles );
        for ( Result<Integer> resultEntry : resultSet )
        {
            System.out.println ( String.format ( "Item: %08X, Error: %08X", resultEntry.getValue (), resultEntry.getErrorCode () ) );
        }

        // set client handles
        System.out.println ( "Set client handles" );
        Integer[] clientHandles = new Integer[serverHandles.length];
        for ( int i = 0; i < serverHandles.length; i++ )
        {
            clientHandles[i] = i;
        }
        itemManagement.setClientHandles ( serverHandles, clientHandles );

        System.out.println ( "Create async IO 2.0 object" );
        OPCAsyncIO2 asyncIO2 = group.getAsyncIO2 ();
        // connect handler

        System.out.println ( "attach" );
        EventHandler eventHandler = group.attach ( new DumpDataCallback () );
        /*
        System.out.println ( "attach..enable" );
        asyncIO2.setEnable ( true );
        System.out.println ( "attach..refresh" );
        asyncIO2.refresh ( (short)1, 1 );
        */
        // sleep
        try
        {
            System.out.println ( "Waiting..." );
            Thread.sleep ( 10 * 1000 );
        }
        catch ( InterruptedException e )
        {
            // TODO Auto-generated catch block
            e.printStackTrace ();
        }

        eventHandler.detach ();

        // set them inactive
        System.out.println ( "In-Active" );
        itemManagement.setActiveState ( false, serverHandles );

        // finally remove them again
        System.out.println ( "Remove" );
        itemManagement.remove ( serverHandles );
    }

    @SuppressWarnings("unused")
    public static void main ( String[] args ) throws IllegalArgumentException, UnknownHostException, JIException
    {
        TestConfiguration configuration = new MatrikonSimulationServerConfiguration ();

        OPCServer server = null;
        try
        {
            JISystem.setAutoRegisteration ( true );
            JISystem.setLogLevel ( Level.ALL );

            _session = JISession.createSession ( args[1], args[2], args[3] );
            
            //JIComServer comServer = new JIComServer ( JIClsid.valueOf ( configuration.getCLSID () ), args[0], _session );
            JIComServer comServer = new JIComServer ( JIProgId.valueOf ( _session, configuration.getProgId () ), args[0], _session );

            IJIComObject serverObject = comServer.createInstance ();
            server = new OPCServer ( serverObject );

            OPCGroupStateMgt group = server.addGroup ( "test", true, 100, 1234, 60, 0.0f, 1033 );

            testItems ( server, group, configuration.getReadItems () );
            server.removeGroup ( group, true );
        }
        catch ( JIException e )
        {
            e.printStackTrace ();
            showError ( server, e.getErrorCode () );
        }
        finally
        {
            if ( _session != null )
                JISession.destroySession ( _session );
            _session = null;
        }
    }
}