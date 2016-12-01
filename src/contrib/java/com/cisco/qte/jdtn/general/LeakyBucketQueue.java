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
package com.cisco.qte.jdtn.general;

import java.util.Collection;
import java.util.LinkedList;


/**
 * A FIFO queue which is an element of a LeakyBucketQueueCollection.
 * A LeakyBucketQueue holds items of type LeakBucketQueueElement, which is a
 * marker interface.  A LeakyBucketQueue has a String name and a State, either
 * RUNNING or DELAYED.  The interpretation of this state is left to
 * LeakyBucketQueueCollection.  Methods involving adding, deleting or 
 * interrogating state of the queue are synchronized.  A LeakyBucketQueue also
 * has an associated Object as a property, whose interpretation and use are
 * left to LeakyBucketQueueCollection.
 * <p>
 * Thread-safe.
 */
public class LeakyBucketQueue {

	/**
	 * States of a LeakyBucketQueueState
	 */
	public enum LeakyBucketQueueState {
		RUNNING,
		DELAYED
	}
	
	private String _name;
	private LinkedList<LeakyBucketQueueElement> _queue = 
		new LinkedList<LeakyBucketQueueElement>();
	private LeakyBucketQueueState _state = LeakyBucketQueueState.RUNNING;
	private Object _associatedObject = null;
	
	/**
	 * Construct a LeakyBucketQueue with given name and associated Object
	 * @param name Name
	 * @param associatedObject Associated Object
	 */
	public LeakyBucketQueue(String name, Object associatedObject) {
		_name = name;
		_associatedObject = associatedObject;
	}
	
	/**
	 * Add given LeakyBucketQueueElement to this LeakyBucketQueue.
	 * @param element Given LeakyBucketQueue
	 */
	public synchronized void add(LeakyBucketQueueElement element) {
		_queue.add(element);
		notify();
	}
	
	/**
	 * Determine if this LeakyBucketQueue has any items enqueued to it.
	 * @return True if this LeakyBucketQueue has any items enqueued.
	 */
	public synchronized boolean isElementReady() {
		return !_queue.isEmpty();
	}
	
	/**
	 * Dequeue the next LeakyBucketQueueElement from this LeakyBucketQueue.
	 * We do not block.
	 * @return next LeakyBucketQueueElement in the LeakyBucketQueue, or null
	 * if none.
	 */
	public synchronized LeakyBucketQueueElement get() {
		try {
			return _queue.removeFirst();
		} catch (IndexOutOfBoundsException e) {
			return null;
		}
	}
	
	/**
	 * Dequeue an element with wait if no elements available
	 * @param waitMSecs Maximum amount of time to wait for elements available
	 * @return Next Queue Element or null if timed out
	 * @throws InterruptedException If interrupted during operation
	 */
	public synchronized LeakyBucketQueueElement getWait(long waitMSecs)
	throws InterruptedException {
		while (_queue.isEmpty()) {
			_queue.wait(waitMSecs);
		}
		return get();
	}
	
	/**
	 * Clear the queue
	 */
	public synchronized void clear() {
		_queue.clear();
	}
	
	/**
	 * Get number of elements currently enqueued
	 * @return What I said
	 */
	public synchronized int size() {
		return _queue.size();
	}
	
	/**
	 * Drain this LeakyBucketQueue to the given Collection
	 * @param c Given Collection
	 * @return Number of elements drained
	 */
	public synchronized int drainTo(Collection<? super LeakyBucketQueueElement> c) {
		int result = _queue.size();
		while (!_queue.isEmpty()) {
			LeakyBucketQueueElement e = _queue.removeFirst();
			c.add(e);
		}
		return result;
	}
	
	/**
	 * Remove all elements which match given predicate
	 * @param predicate A method we will call for each element in the queue,
	 * passing the element and the given argument
	 * @param argument Given argument
	 */
	public synchronized void removeElementsMatching(QueuePredicate predicate, Object argument) {
		LinkedList<LeakyBucketQueueElement> deleteList =
			new LinkedList<LeakyBucketQueueElement>();
		for (LeakyBucketQueueElement element : _queue) {
			if (predicate.matches(element, argument)) {
				deleteList.add(element);
			}
		}
		for (LeakyBucketQueueElement element : deleteList) {
			_queue.remove(element);
		}
	}
	
	/**
	 * Method to implement for removeElementsMatching()
	 */
	public interface QueuePredicate {
		public boolean matches(LeakyBucketQueueElement element, Object object);
	}
	
	/**
	 * Dump this LeakyBucketQueue
	 * @param indent How much to indent
	 * @param detailed Whether want details
	 * @return String containing dump
	 */
	public synchronized String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(indent + "LeakyBucketQueue " + getName() + "\n");
		sb.append(indent + "  State=" + getState() + "\n");
		if (detailed) {
			for (LeakyBucketQueueElement el : _queue) {
				sb.append(el.dump(indent + "  ", detailed));
			}
		}
		return sb.toString();
	}
	
	/**
	 * Get the State of this LeakyBucketQueue
	 * @return State
	 */
	protected LeakyBucketQueueState getState() {
		return _state;
	}
	
	/**
	 * Set the State of this LeakyBucketQueue
	 * @param state Desired State
	 */
	protected void setState(LeakyBucketQueueState state) {
		_state = state;
	}
	
	/**
	 * Get the associated Object property of this LeakyBucketQueue
	 * @return Associated State
	 */
	protected Object getAssociatedObject() {
		return _associatedObject;
	}
	
	/**
	 * Get the name property of this LeakyBucketQueue
	 * @return Name property
	 */
	protected String getName() {
		return _name;
	}
	
	/**
	 * Set the name property of this LeakyBucket
	 * @param name Name
	 */
	public void setName(String name) {
		_name = name;
	}
	
}
