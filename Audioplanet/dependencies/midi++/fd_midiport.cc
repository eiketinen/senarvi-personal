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

    $Id: fd_midiport.cc,v 1.1 2007/07/04 07:26:39 seppo Exp $
*/

#include <fcntl.h>
#include <errno.h>

#include <pbd/error.h>
#include <pbd/pathscanner.h>

#include <midi++/types.h>
#include <midi++/fd_midiport.h>

using namespace std;
using namespace MIDI;

string *FD_MidiPort::midi_dirpath = 0;
string *FD_MidiPort::midi_filename_pattern = 0;

FD_MidiPort::FD_MidiPort (PortRequest &req, 
			  const string &dirpath,
			  const string &pattern) 
	: Port (req)
{	
	open (req);

	if (_fd < 0) {
		switch (errno) {
		case EBUSY:
			error << "MIDI: port device in use" << endmsg;
			req.status = PortRequest::Busy;
			break;
		case ENOENT:
			error << "MIDI: no such port device" << endmsg;
			req.status = PortRequest::NoSuchFile;
			break;
		case EACCES:
			error << "MIDI: access to port denied" << endmsg;
			req.status = PortRequest::NotAllowed;
			break;
		default:
			req.status = PortRequest::Unknown;
		} 
	} else {
		_ok = true;
		req.status = PortRequest::OK;

		if (midi_dirpath == 0) {
			midi_dirpath = new string (dirpath);
			midi_filename_pattern = new string (pattern);
		}

		if (req.mode & O_NONBLOCK == 0) {
			/* we unconditionally set O_NONBLOCK during
			   open, but the request didn't ask for it,
			   so remove it.
			*/

			int flags = fcntl (_fd, F_GETFL, 0);
			fcntl (_fd, F_SETFL, flags & ~(O_NONBLOCK));
		}
	}
}

void
FD_MidiPort::open (PortRequest &req)

{
	int mode = req.mode | O_NONBLOCK;
	_fd = ::open (req.devname, mode);
}

vector<string *> *
FD_MidiPort::list_devices ()

{
	PathScanner scanner;

	return scanner (*midi_dirpath, *midi_filename_pattern, false, true);
}	

int
FD_MidiPort::selectable () const

{
	long flags;
	
	/* turn on non-blocking mode, since we plan to use select/poll
	   to tell us when there is data to read.
	*/

	flags = fcntl (_fd, F_GETFL);
	flags |= O_NONBLOCK;

	if (fcntl (_fd, F_SETFL, flags)) {
		error << "FD_MidiPort: could not turn on non-blocking mode"
		      << " (" << strerror (errno) 
		      << ')'
		      << endmsg;

		return -1;
	}

	return _fd;
}


int
FD_MidiPort::do_slow_write (byte *msg, unsigned int msglen)

{
	size_t n;
	size_t i;

	for (n = 0; n < msglen; n++) {

		if (::write (_fd, &msg[n], 1) != 1) {
			break;
		}

		bytes_written++;
		for (i = 0; i < slowdown * 10000; i++);
	}


	if (n && output_parser) {
		output_parser->raw_preparse (*output_parser, msg, n);
		for (unsigned int i = 0; i < n; i++) {
			output_parser->scanner (msg[i]);
		}
		output_parser->raw_postparse (*output_parser, msg, n);
	}
	
	return n;
}
