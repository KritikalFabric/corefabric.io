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
package com.cisco.qte.jdtn.component;

/**
 * Superclass for components.  A component collects some useful features:
 * <ul>
 *   <li>It has a name (not necessarily unique)
 *   <li>It can be dumped
 *   <li>You can clear its statistics
 * </ul>
 */
public abstract class AbstractComponent implements IComponent {

	private String _name;
	
	/**
	 * Constructor
	 * @param name Name of component (need not be unique)
	 */
	public AbstractComponent(String name) {
		_name = new String(name);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("AbstractComponent{");
		sb.append("name=" + _name + ", ");
		sb.append("}");
		return sb.toString();
	}

	/**
	 * Dump this component
	 * @param indent Amount of indentation
	 * @param detailed Whether detailed dump wanted
	 * @return String containing the dump
	 */
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "AbstractComponent\n");
		sb.append(indent + "  Name=" + _name + "\n");
		return sb.toString();
	}
	
	/** Name property */
	public String getName() {
		return _name;
	}

	/** Name property */
	public void setName(String name) {
		_name = new String(name);
	}
	
	/**
	 * @see com.cisco.qte.jdtn.component#IComponent
	 */
	@Override
	public void clearStatistics() {
		// Nothing
	}

}
