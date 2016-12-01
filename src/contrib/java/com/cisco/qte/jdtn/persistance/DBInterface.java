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
 * Interface to Bundles database.
 */
public interface DBInterface {

	/**
	 * Open the Database, creating it if necessary
	 * @param startClean True => database will be emptied; False => database
	 * will retain its content.
	 */
	public void openDB(boolean startClean) throws DBInterfaceException;
	
	/**
	 * Close the Database (allow for the possibility of it being reopened later.
	 * @throws DBInterfaceException on errors
	 */
	public void closeDB() throws DBInterfaceException;
	
	/**
	 * Execute an Insert SQL statement w/in a single transaction
	 * @param stmt The Insert SQL statement.
	 * @throws DBInterfaceException on errors
	 */
	public void executeInsert(String stmt) throws DBInterfaceException;
	
	/**
	 * Execute a Delete SQL statement w/in a single transaction
	 * @param stmt The Delete SQL statement.
	 * @throws DBInterfaceException on errors
	 */
	public void executeDelete(String stmt) throws DBInterfaceException;
	
	/**
	 * Execute an Update SQL statement w/in a single transaction
	 * @param stmt The Update SQL statement
	 * @throws DBInterfaceException on errors
	 */
	public void executeUpdate(String stmt) throws DBInterfaceException;
	
	/**
	 * Execute a Query SQL statement.
	 * @param stmt The Query SQL statement
	 * @return An object describing the query results
	 * @throws DBInterfaceException on errors
	 */
	public QueryResults executeQuery(String stmt) throws DBInterfaceException;
	
	/**
	 * Clear the database of all records
	 * @throws DBInterfaceException
	 */
	public void clear() throws DBInterfaceException;
	
}
