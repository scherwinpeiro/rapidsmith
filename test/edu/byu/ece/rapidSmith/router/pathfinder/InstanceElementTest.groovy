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

package edu.byu.ece.rapidSmith.router.pathfinder

import edu.byu.ece.rapidSmith.router.InstanceElement
import spock.lang.Specification
import spock.lang.Unroll
/**
 * Created by wenzel on 25.04.17.
 */
class InstanceElementTest extends Specification {
	@Unroll
	def "Simplify LUT String"(String input, String out) {
		expect:
		InstanceElement.simplifyLutConfigString(input) == out

		where:
		input                  | out
		"#OFF"                 | "#OFF"
		"#LUT:O6=1"            | "#LUT:O6=1"
		"#LUT:O5=0"            | "#LUT:O5=0"
		"#LUT:O5=0*0"          | "#LUT:O5=0"
		"#LUT:O5=0*(0)"        | "#LUT:O5=0"
		"#LUT:O5=1*0"          | "#LUT:O5=0"
		"#LUT:O5=1*(0)"        | "#LUT:O5=0"
		"#LUT:O5=0*1"          | "#LUT:O5=0"
		"#LUT:O5=0*(1)"        | "#LUT:O5=0"
		"#LUT:O5=1*1"          | "#LUT:O5=1"
		"#LUT:O5=1*1*1*1*1"    | "#LUT:O5=1"
		"#LUT:O5=1*(1)"        | "#LUT:O5=1"
		"#LUT:O5=(A6+~A6)*(1)" | "#LUT:O5=1"
		"#LUT:O5=(A6+~A5)*(1)" | "#LUT:O5=(A6+~A5)*1"
		"#LUT:O5=(A6+~A6)*(0)" | "#LUT:O5=0"
	}
}
