/***********************************************************************
 *  J a v a G P S - GPS access library and Java API                    *
 *  Copyright (C) 2001 Ulrich Walther                                  *
 *                                                                     *
 *  This program is free software; you can redistribute it and/or      *
 *  modify it under the terms of the GNU General Public License as     *
 *  published by the Free Software Foundation; either version 2 of     *
 *  the License, or (at your option) any later version.                *
 *                                                                     *
 *  This program is distributed in the hope that it will be useful,    *
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of     *
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU   *
 *  General Public License for more details.                           *
 *                                                                     *
 *  You should have received a copy of the GNU General Public          *
 *  License along with this program; if not, write to the Free         *
 *  Software Foundation, Inc., 59 Temple Place, Suite 330, Boston,     *
 *  MA 02111-1307 USA                                                  *
 ***********************************************************************/

package org.iu.gps;

import java.lang.reflect.*;
import java.util.logging.Logger;


/**
 *  NMEA0183 message parser. Uses reflection to be able to easily specify new
 *  NMEA messages (see below: SENTENCE_DEFINITIONS).
 *
 *@author    walther
 */
public class NMEA
{
    private static Logger logger_ = Logger.getLogger("org.iu.gps");


    // Here are the definitions of GPS sentences we can handle
    // Note: the argument names correspond to members of class GPSInfo and
    //       are automatically converted to the type of the members.

    /**
     *  Definition of understood NMEA0183 sentences. The values given here as
     *  symbolic names are taken as field names of the info object given to the
     *  parse method; values from the sentence are automatically converted to the
     *  field's type in the info object. E.g. the definition of<br>
     *  <code>$GPTST,lat,long</code><br>
     *  specifies a new message that has the header <code>$GPTST</code>, and two
     *  parameters lat and long. Those two values are automatically parsed and
     *  stored inside the GPSInfo class, in the fields with names lat and long. The
     *  type of the value is automatically converted to fit the type of the fields.
     */
    public final static String[] SENTENCE_DEFINITIONS = new String[]{
	"$GPGGA,utc,latitude,northHemi,longitude,eastHemi,quality," +
	"numberOfSatellites,horDilution,height,,geoidalHeight,,diffCorrection,diffStationId,",
	"$GPGSA,mode,fixtype,s1,s2,s3,s4,s5,s6,s7,s8,s9,s10,s11,s12," +
	"posDilution,horDilution,verDilution,",
	"$GPGSV,gsvnum,gsvcur,satInView",
	"$GPRMC,utc,status,latitude,northHemi,longitude,eastHemi," +
	"speedOverGroundKnots,courseOverGround,utcdate,magnVariation,magnVarDirection,",
	"$GPVTG,courseOverGround,,magnCourse,,speedOverGroundKnots,,speedOverGround,",
	"$LCGLL,latitude,northHemi,longitude,eastHemi,utc,",
	"$LCVTG,courseOverGround,,magnCourse,,speedOverGroundKnots,,speedOverGround,",
	"$PGRME,HPE,,VPE,,EPE,",
	"$PGRMF,GPSWeek,GPSSeconds,utcdate,utc,GPSLeapSecondCount," +
	"latitude,northHemi,longitude,eastHemi,mode,fixType,speedOverGround,courseOverGround,",
	"$PGRMT,GPSModel,romChecksum,recvFailure,storedDataLost,timeLost," +
	"oscillatorDrift,dataCollection,boardTemperature,boardConfig",
	"$PGRMV,eastVelocity,northVelocity,upVelocity,",
    };


    /**
     * Parse given NMEA0183 message (usually from GPS device) and
     * convert the parsed values to fit into the GPSInfo structure.
     *
     * @param  sentence  NMEA sentence to parse
     * @param  info      Object to fill in with values from the sentence
     * @return           <code>true</code> if successful, <code>false</code> otherwise
     */
    public static boolean parse(String sentence, GPSInfo info)
    {
	try
	{
	    String type = sentence.substring(0, 7);
	    for (int i = 0; i < SENTENCE_DEFINITIONS.length; i++)
	    {
		if (SENTENCE_DEFINITIONS[i].startsWith(type))
		{
		    parseSentence(sentence.substring(7), SENTENCE_DEFINITIONS[i].substring(7), info);
		    return true;
		}
	    }

	    logger_.warning("Sentence " + type + " not supported.");
	}
	catch (Exception e)
	{
	    logger_.warning("Cannot parse: " + sentence);
	    e.printStackTrace();
	}

	return false;
    }


    // Increase all members of an object that end with "Age"
    static void increaseAge( Object o )
    {
	Class c = o.getClass();
	Field[] f = c.getFields();

	for ( int i = 0; i < f.length; i++ )
	{
	    if ( f[i].getName().endsWith( "Age" ) )
	    {
		try
		{
		    int age = f[i].getInt( o );
		    if ( age < 9999 )
		    {
			f[i].setInt( o, age + 1 );
		    }
		}
		catch ( Exception e )
		{
		}
	    }
	}
    }


    // Print all members of an object
    static void printAll(Object o)
    {
	Class c = o.getClass();
	Field[] f = c.getFields();

	for (int i = 0; i < f.length; i++)
	{
	    logger_.finest("Field listing:");
	    try
	    {
		logger_.finest(f[i].getName() + " = " + f[i].get(o));
	    }
	    catch (IllegalAccessException e)
	    {
		logger_.finest(f[i].getName() + " cannot be accessed");
	    }
	}
    }


    // Set all members of an object that end with "Age" to -1 == inifite age
    static void infiniteAge( Object o )
    {
	Class c = o.getClass();
	Field[] f = c.getFields();

	for ( int i = 0; i < f.length; i++ )
	{
	    if ( f[i].getName().endsWith( "Age" ) )
	    {
		try
		{
		    f[i].setInt( o, 9999 );
		}
		catch ( Exception e )
		{
		}
	    }
	}
    }


    static void parseSentence( String sentence, String syntax, Object result )
    {
	NMEATokenizer t = new NMEATokenizer( sentence );
	NMEATokenizer t2 = new NMEATokenizer( syntax );
	String s;
	String s2;
	Field f;
	Field fage;
	Class c = result.getClass();

	while ( t.hasMoreTokens() && t2.hasMoreTokens() )
	{
	    s = t.nextToken();
	    s2 = t2.nextToken();
	    if ( s.length() == 0 || s2.length() == 0 )
	    {
		continue;
	    }

	    logger_.finest(s2 + " = " + s);

	    try
	    {
		f = c.getField( s2 );
	    }
	    catch ( NoSuchFieldException e )
	    {
		logger_.warning("NoSuchFieldException while parsing a sentence: " + e);
		continue;
	    }

	    boolean gotValue = true;

	    try
	    {
		String type = f.getType().getName();

		if (type.equals("java.lang.String"))
		{
		    f.set(result, s);
		}
		else if (type.equals("byte"))
		{
		    f.setByte(result, new Byte(s).byteValue());
		}
		else if (type.equals("char"))
		{
		    f.setChar(result, new String(s).charAt(0));
		}
		else if (type.equals("int"))
		{
		    f.setInt(result, new Integer(s).intValue());
		}
		else if (type.equals("float"))
		{
		    f.setFloat(result, new Double(s).floatValue());
		}
		else if (type.equals("double"))
		{
		    f.setDouble(result, new Double(s).doubleValue());
		}
		else
		{
		    logger_.warning("Argument " + s2 + " cannot be converted to " + type);
		    gotValue = false;
		}

	    }
	    catch (Exception e)
	    {
		logger_.warning("Exception while parsing a sentence: " + e);
		continue;
	    }

	    // reset age of the received value to zero
	    // (before reception of a value of that type,
	    //  it remains at state -1 == infinite age)
	    if ( gotValue )
	    {
		try
		{
		    f = c.getField( s2 + "Age" );
		    f.setInt( result, 0 );
		}
		catch ( Exception e )
		{
		}
	    }
	}
    }


    static String checkSum( String msg )
    {
	// perform NMEA checksum calculation
	int chk = 0;
	for ( int i = 1; i < msg.length(); i++ )
	{
	    chk ^= msg.charAt( i );
	}

	String chk_s = Integer.toHexString( chk ).toUpperCase();

	// checksum must be 2 characters!
	while ( chk_s.length() < 2 )
	{
	    chk_s = "0" + chk_s;
	}

	return chk_s;
    }


    // calculate checksum of NMEA message and compare
    static boolean check( String msg )
    {
	int msglen = msg.length();

	if ( msglen > 4 )
	{
	    if ( msg.charAt( msglen - 3 ) == '*' )
	    {
		// perform NMEA checksum calculation

		String chk_s = checkSum( msg.substring( 0, msglen - 3 ) );

		// compare checksum to encoded checksum in msg
		return ( msg.substring( msglen - 2, msglen ).equals( chk_s ) );
	    }
	    else
	    {
		// message doesn't have a checksum: accept it
		return true;
	    }
	}

	// don't accept messages without checksum
	return false;
    }


    static String addCheckSum(String msg)
    {
	return msg + "," + checkSum(msg + ",") + "*" + ((char)13) + ((char)10);
    }
}
