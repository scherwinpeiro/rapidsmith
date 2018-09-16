/*
 * _______________________________________________________________________________
 *
 *  Copyright (c) 2012 TU Dresden, Chair for Embedded Systems
 *  Copyright (c) 2013-2016 TU Darmstadt, Computer Systems Group
 *  (http://www.rs.tu-darmstadt.de) All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  1. Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *  3. All advertising materials mentioning features or use of this software
 *     must display the following acknowledgement: "This product includes
 *     software developed by the TU Dresden Chair for Embedded Systems, TU Darmstadt Computer Systems Group and
 *     its contributors."
 *
 *  4. Neither the name of the TU Dresden Chair for Embedded Systems, TU Darmstadt Computer Systems Group nor the
 *     names of its contributors may be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY TU DRESDEN CHAIR FOR EMBEDDED SYSTEMS, TU DARMSTADT COMPUTER SYSTEMS GROUP AND
 *  CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 *  BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 *  FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *  REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 *  TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 *  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * _______________________________________________________________________________
 */

package edu.byu.ece.rapidSmith.util;

import edu.byu.ece.rapidSmith.device.database.ClasspathDeviceDatabase;
import edu.byu.ece.rapidSmith.device.database.DeviceDatabase;
import edu.byu.ece.rapidSmith.device.database.FileDeviceDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class DeviceDatabaseProvider {
	private static final Logger logger = LoggerFactory.getLogger(DeviceDatabaseProvider.class);

	private static DeviceDatabase deviceDatabase;

	public static DeviceDatabase loadDeviceDatabase() {
		List<Supplier<DeviceDatabase>> databases = Arrays.asList(
				FileDeviceDatabase::new,
				ClasspathDeviceDatabase::new
		);

		for (Supplier<DeviceDatabase> supplier : databases) {
			DeviceDatabase db = supplier.get();
			if (db.isDatabaseAvailable()) {
				logger.info("Loading devices from {}",db.getClass().getSimpleName());
				return db;
			}
		}
		throw new RuntimeException("No Device Database is available. Either set RAPIDSMITH_PATH environment variable or put the device files on the classpath.");
	}

	public static DeviceDatabase getDeviceDatabase() {
		if (deviceDatabase == null) {
			deviceDatabase = loadDeviceDatabase();
		}
		return deviceDatabase;
	}
}
