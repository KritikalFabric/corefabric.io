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

import java.util.ConcurrentModificationException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An abstract class implementing a Thread which sequentially processes events
 * enqueued to it on an ArrayBlockingQueue.
 * Sub-classes provide the processing of each event.
 */
public abstract class AbstractEventProcessorThread extends AbstractEventProcessor
implements Runnable {
	// Amt of time to wait for StopEvent to be processed and notification back
	private static final long WAIT_FOR_STOP_MSECS = 100L;

	public static final Logger _logger =
		Logger.getLogger(AbstractEventProcessorThread.class.getCanonicalName());
	
	private ArrayBlockingQueue<IEvent> _eventQueue;
	private Thread _thread;
	private int _capacity;
	public long nEnqueues = 0;
	public long nDequeues = 0;
	
	/**
	 * Constructor.  Initialization.  Call start() to actually start the
	 * Thread.
	 * @param name Name of the Thread.  Must be non-null.
	 * @param capacity Capacity, in number of items, of the underlying
	 * ArrayBlockingQueue.  Must be > 0.
	 * @throws IllegalArgumentException on bad values for arguments
	 */
	public AbstractEventProcessorThread(String name, int capacity)
	throws IllegalArgumentException {
		super(name);
		if (name == null) {
			throw new IllegalArgumentException("Null 'name' argument");
		}
		if (capacity <= 0) {
			throw new IllegalArgumentException("'capacity' argument must be > 0");
		}
		_capacity = capacity;
		_eventQueue = new ArrayBlockingQueue<IEvent>(capacity);
	}
	
	/**
	 * Start the event processing Thread
	 */
	@Override
	protected void startImpl() {
		_thread = new Thread(this, getName());
		_thread.start();
	}
	
	/**
	 * Stop the event processing Thread.  We send a Stop event to the event
	 * processing thread.  When it gets around to that event, the Thread will
	 * kill itself.  We wait for it to reach the point where it has processed
	 * that event.
	 * @throws InterruptedException If receive InterruptedException waiting
	 * for space on the ArrayBlockingQueue or waiting for the Stop event to
	 * be processed.
	 */
	@Override
	protected void stopImpl() throws InterruptedException {
		StopEvent event = new StopEvent("StopEvent");
		processEvent(event);
		event.waitForSync(WAIT_FOR_STOP_MSECS);
	}
	
	/**
	 * Wait for the event processing Thread to die.
	 * @throws InterruptedException If interrupted before that happens.
	 */
	public void join() throws InterruptedException {
		_thread.join();
	}
	
	/**
	 * Wait for event processing Thread to die, up to a maximum number of
	 * mSecs.
	 * @param maxWait Maximum number of mSecs to wait for it to die.  Must be
	 * > 0
	 * @throws IllegalArgumentException if argument has bad value.
	 * @throws InterruptedException If receive InterruptException during the
	 * join.
	 */
	public void join(long maxWait)
	throws IllegalArgumentException, InterruptedException {
		if (maxWait <= 0) {
			throw new IllegalArgumentException("'maxWait' argument must be > 0");
		}
		_thread.join(maxWait);
	}
	
	/**
	 * Enqueue an Event to the event processing Thread
	 * @param event The Event to enqueue.  Must be non-null.
	 * @throws IllegalArgumentException if event is null.
	 * @throws IllegalStateException if event process has not been started.
	 * @throws InterruptedException If calling Thread is interrupted while
	 * waiting for space in the ArrayBlockingQueue.
	 */
	@Override
	public void processEvent(IEvent event)
	throws IllegalArgumentException, IllegalStateException, InterruptedException {
		if (event == null) {
			throw new IllegalArgumentException("Null 'event' argument");
		}
		if (!(event instanceof StopEvent) && !isStarted()) {
			throw new IllegalStateException(
					"Event Processor " + getName() + " has not been started");
		}
		nEnqueues++;
		_eventQueue.put(event);
	}
	
	/**
	 * Event processing thread.  Receives each enqueued event, and calls
	 * sub-class defined method to process the event.
	 */
	public void run() {
		try {
			while (!Thread.interrupted()) {
				IEvent event = _eventQueue.take();
				nDequeues++;
				if (event instanceof AbstractComponent) {
					AbstractComponent ac = (AbstractComponent)event;
					_logger.finer(getName() + " processing event: " + ac.dump("", true));
				}
				processEventImpl(event);
				if (event instanceof StopEvent) {
					_logger.fine(getName() + " thread stopped");
					StopEvent stopEvent = (StopEvent)event;
					_eventQueue.clear();
					stopEvent.notifySyncProcessed();
					break;
				}
			}
		} catch (InterruptedException e) {
			_logger.fine(getName() + " thread interrupted");
		} catch (Exception e) {
			_logger.log(Level.SEVERE, getName() + " thread", e);
		}
	}
	
	@Override
	public void clearStatistics() {
		nEnqueues = 0;
		nDequeues = 0;
		super.clearStatistics();
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("EventProcessor(");
		sb.append(super.toString());
		sb.append(")");
		return sb.toString();
	}

	/**
	 * Dump this object.
	 * @param indent Amount of indentation
	 * @param detailed Whether detailed info desired
	 * @return String containing dump
	 */
	@Override
	public String dump(String indent, boolean detailed) {
		StringBuilder sb = new StringBuilder(indent + "EventProcessorThread\n");
		sb.append(indent + "  nEnqueues=" + nEnqueues + "\n");
		sb.append(indent + "  nDequeues=" + nDequeues + "\n");
		sb.append(indent + "  nQueueElements=" + _eventQueue.size() + "\n");
		sb.append(super.dump(indent + "  ", detailed));
		if (detailed) {
			try {
				for (IEvent event : _eventQueue) {
					if (event instanceof AbstractComponent) {
						AbstractComponent ac = (AbstractComponent)event;
						sb.append(ac.dump(indent + "  ", detailed));
					}
				}
			} catch (ConcurrentModificationException e) {
				sb.append("\nDump aborted due to ConcurrentModificationException");
			}
		}
		return sb.toString();
	}
	
	public int getCapacity() {
		return _capacity;
	}
	
}
