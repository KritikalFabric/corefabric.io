/**
Copyright (c) 2011, Cisco Systems, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

    * Neither the name of the Cisco Systems, Inc. nor the names of its
    contributors may be used to endorse or promote products derived from this
    software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.cisco.qte.jdtn.persistance;

/**
 * Interface for query results
 */
public interface QueryResults {

	/**
	 * Position to next result
	 * @return true if there is a next result; false otherwise
	 * @throws DBInterfaceException on errors
	 */
	public boolean next() throws DBInterfaceException;
	
	/**
	 * Get i'th field of current result
	 * @param ix Index of result to be gotten; starting at 1
	 * @return Result
	 * @throws DBInterfaceException on errors
	 */
	public String getString(int ix) throws DBInterfaceException;
	
	/**
	 * Get i'th field of current result as an int
	 * @param ix Index of result to be gotten; starting at 1
	 * @return Result
	 * @throws DBInterfaceException
	 */
	public int getInt(int ix) throws DBInterfaceException;
	
	/**
	 * Get i'th field of current result as a long
	 * @param ix Index of result to be gotten; starting at 1
	 * @return Result
	 * @throws DBInterfaceException
	 */
	public long getLong(int ix) throws DBInterfaceException;
	
	/**
	 * Get i'th field of current result as a boolean
	 * @param ix Index of result to be gotten; starting at 1
	 * @return Result
	 * @throws DBInterfaceException
	 */
	public boolean getBoolean(int ix) throws DBInterfaceException;
	
	/**
	 * Declare that we are done with this QueryResults object
	 * @throws DBInterfaceException on errors
	 */
	public void close() throws DBInterfaceException;
	
}
