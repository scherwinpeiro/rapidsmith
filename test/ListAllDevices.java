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

import de.tu_darmstadt.rs.util.table.Column;
import de.tu_darmstadt.rs.util.table.TableFormat;
import de.tu_darmstadt.rs.util.table.TableFormatter;

import edu.byu.ece.rapidSmith.device.database.DeviceDatabase;
import edu.byu.ece.rapidSmith.util.DeviceDatabaseProvider;
import edu.byu.ece.rapidSmith.util.FamilyType;

public class ListAllDevices {

	public static void main(String[] args) {
		final DeviceDatabase deviceDatabase = DeviceDatabaseProvider.getDeviceDatabase();
		TableFormatter tf = new TableFormatter(TableFormat.CHARACTERS, Column.STRING);
		tf.<Object>addTree(
				deviceDatabase,
				obj -> {
					if (obj instanceof DeviceDatabase)
						return deviceDatabase.getAvailableFamilies();
					if (obj instanceof FamilyType) {
						return deviceDatabase.getAvailableParts((FamilyType) obj);
					}
					return null;
				},
				obj -> {
					if (obj instanceof DeviceDatabase)
						return new String[] {"Devices"};
					return new String[] {obj.toString()};
				}
		);
		tf.output().forEach(System.out::println);
	}

}
