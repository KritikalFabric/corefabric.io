/**
Copyright (c) 2010, Cisco Systems, Inc.
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
package com.cisco.qte.jdtn.bp;

import java.util.ArrayList;

import com.cisco.qte.jdtn.component.AbstractStartableComponent;

/**
 * A component which maintains a set of registered Routers, and which responds
 * to routing requests by calling each registered Router in turn, trying to
 * find a Route to satisfy the request.
 */
public class RouterManager extends AbstractStartableComponent
implements Router {

	public enum RouterPriority {
		FIRST,
		LAST,
		DONT_CARE
	}
	
	private static RouterManager _instance = null;
	private ArrayList<Router> _routers = new ArrayList<Router>();
	
	/**
	 * Get singleton instance of this component
	 * @return Singleton instance
	 */
	public static RouterManager getInstance() {
		if (_instance == null) {
			_instance = new RouterManager();
		}
		return _instance;
	}
	
	/**
	 * Constructor, restricted access to enforce singleton pattern
	 */
	protected RouterManager() {
		super("RouterManager");
	}
	
	/* (non-Javadoc)
	 * @see com.cisco.qte.jdtn.component.AbstractStartableComponent#startImpl()
	 */
	@Override
	protected void startImpl() {
		// Nothing
	}

	/* (non-Javadoc)
	 * @see com.cisco.qte.jdtn.component.AbstractStartableComponent#stopImpl()
	 */
	@Override
	protected void stopImpl() throws InterruptedException {
		_routers.clear();
	}
	
	
	
	
	/**
	 * Find a Route which specifies the next hop for forwarding the given
	 * Bundle.
	 * @param bundle Given bundle
	 * @return Route determined, or null if no route found
	 */
	@Override
	public Route findRoute(Bundle bundle) {
		ArrayList<Router> routers = null;
		synchronized (this) {
			routers = new ArrayList<Router>(_routers);
		}
		for (Router router : routers) {
			Route route = router.findRoute(bundle);
			if (route != null) {
				return route;
			}
		}
		return null;
	}

	public void registerRouter(Router router, RouterPriority priority) {
		synchronized (this) {
			switch (priority) {
			case FIRST:
				_routers.add(0, router);
				break;
			default:
				_routers.add(router);
				break;
			}
		}
	}
	
	public void unregisterRouter(Router router) {
		synchronized (this) {
			_routers.remove(router);
		}
	}

}
