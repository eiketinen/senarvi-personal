/*
    Copyright (C) 1999 Paul Barton-Davis 

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.

    $Id: fd_midiport.h,v 1.1 2007/07/04 07:26:39 seppo Exp $
*/

#ifndef __fd_midiport_h__
#define __fd_midiport_h__

#include <vector>
#include <string>

using namespace std;

#include <fcntl.h>
#include <unistd.h>
#include <errno.h>

#include <midi++/port.h>
#include <midi++/port_request.h>

namespace MIDI {

class FD_MidiPort : public Port

{
  public:
	FD_MidiPort (PortRequest &req, 
		     const string &dirpath,
		     const string &pattern);

	virtual ~FD_MidiPort () {
		::close (_fd);
	}

	virtual int selectable() const;
	static vector<string *> *list_devices ();

  protected:
	int _fd;
	virtual void open (PortRequest &req);

	virtual int write (byte *msg, size_t msglen) {
		int nwritten;
		
		if ((_mode & O_ACCMODE) == O_RDONLY) {
			return -EACCES;
		}
		
		if (slowdown) {
			return do_slow_write (msg, msglen);
		}

		if ((nwritten = ::write (_fd, msg, msglen)) > 0) {
			bytes_written += nwritten;
			
			if (output_parser) {
				output_parser->raw_preparse 
					(*output_parser, msg, nwritten);
				for (int i = 0; i < nwritten; i++) {
					output_parser->scanner (msg[i]);
				}
				output_parser->raw_postparse 
					(*output_parser, msg, nwritten);
			}
		}
		return nwritten;
	}

	virtual int read (byte *buf, size_t max) {
		int nread;

		if ((_mode & O_ACCMODE) == O_WRONLY) {
			return -EACCES;
		}
		
		if ((nread = ::read (_fd, buf, max)) > 0) {
			bytes_read += nread;

			if (input_parser) {
				input_parser->raw_preparse 
					(*input_parser, buf, nread);
				for (int i = 0; i < nread; i++) {
					input_parser->scanner (buf[i]);
				}	
				input_parser->raw_postparse 
					(*input_parser, buf, nread);
			}
		}
		return nread;
	}
  private:
	static string *midi_dirpath;
	static string *midi_filename_pattern;

	int do_slow_write (byte *msg, unsigned int msglen);
};

}; /*namespace MIDI */

#endif  // __fd_midiport_h__
