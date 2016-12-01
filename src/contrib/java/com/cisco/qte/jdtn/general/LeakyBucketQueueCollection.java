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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
//import java.util.logging.Logger;

import com.cisco.qte.jdtn.general.LeakyBucketQueue.LeakyBucketQueueState;


/**
 * A Collection of Leaky Bucket Queues.  Each queue in the Collection has
 * one producer, and the Collection as a whole has a single consumer.  In
 * other words, it is a many-producer-one-consumer queue.  In
 * addition, each Queue has a state, either RUNNING or DELAYED.
 * <ul>
 *   <li> In RUNNING state, the queue is eligible for items to be removed and
 *   consumed.
 *   <li> In DELAYED state, the queue is frozen for a specific amount of time.
 * </ul>
 * <p>
 * Any and all elements in each Queue must implement LeakyBucketQueueElement,
 * a marker interface.  Each queue in the Collection has the capability of adding
 * LeakyBucketQueueDelayElements.  A LeakyBucketQueueDelayElement is a special
 * kind of queue element which has a delay property which dictates how long
 * the queue is to be in DELAYED state.
 * When a LeakyBucketQueueDelayElement comes off the head of the queue (which
 * can only happen when the Queue is in RUNNING state), the queue is transitioned
 * to DELAYED state, and will remain there for approximately the delay property
 * of the LeakyBucketQueueDelayElement.  While in DELAYED state, no items are
 * removed from the queue.
 * <p>
 * It is left to the producers to decide when to add LeakyBucketQueueDelayElements
 * and how long their associated delays should be.
 * <p>
 * Each Queue in the collection is encapsulated as a LeakyBucketQueue. However,
 * all producer/consumer API is embodied in LeakyBucketQueueCollection.  The
 * LeakyBucketQueue type should be considered opaque.
 * <p>
 * Each Queue in the collection has an associated Object.  This is used as an
 * opaque 'tag' or 'label' on the Queue, allowing producers to tag their own
 * particular Queue.
 * <p>
 * Thread-safe.
 */
public class LeakyBucketQueueCollection {

//	private static final Logger _logger =
//		Logger.getLogger(LeakyBucketQueueCollection.class.getCanonicalName());
	
	private String _name;
	private final Object _sync = new Object();
	private ArrayList<LeakyBucketQueue> _queues =
		new ArrayList<LeakyBucketQueue>();
	private HashMap<Object, LeakyBucketQueue> _queueMap =
		new HashMap<Object, LeakyBucketQueue>();
	private int _currentIndex = 0;
	private Timer _timer = new Timer("LeakyBucketCollectionTimer", true);
	
	/**
	 * Constructor
	 */
	public LeakyBucketQueueCollection(String name) {
		_name = name;
	}
	
	/**
	 * Add the given LeakyBucketQueue to the Collection
	 * @param queue The LeakyBucketQueue to add
	 */
	public void add(LeakyBucketQueue queue) {
		synchronized (_sync) {
			_queues.add(queue);
			_queueMap.put(queue.getAssociatedObject(), queue);
			_sync.notify();
		}
	}
	
	/**
	 * Remove the given LeakyBucketQueue from the Collection
	 * @param queue The LeakyBucketQueue to remove
	 */
	public void remove(LeakyBucketQueue queue) {
		synchronized (_sync) {
			_queues.remove(queue);
			_queueMap.remove(queue.getAssociatedObject());
		}
	}
	
	/**
	 * Get the LeakyBucketQueue associated with given Object
	 * @param associatedObject Given Object
	 * @return Associated LeakyBucketQueue or null if none
	 */
	public LeakyBucketQueue getQueue(Object associatedObject) {
		return _queueMap.get(associatedObject);
	}
	
	/**
	 * Clear this LeakyBucketQueueCollection.  Clear all contained queues,
	 * then remove all LeakyBucketQueues from service.
	 */
	public void clear() {
		synchronized (_sync) {
			for (LeakyBucketQueue queue : _queues) {
				queue.clear();
			}
			_queues.clear();
			_queueMap.clear();
			_timer.cancel();
			_timer.purge();
		}
	}
	
	/**
	 * Get the total number of elements enqueued in all queues of this collection.
	 * @return Number of elements in all queues
	 */
	public int size() {
		int result = 0;
		synchronized (_sync) {
			for (LeakyBucketQueue queue : _queues) {
				result += queue.size();
			}
		}
		return result;
	}
	
	/**
	 * Drain this LeakyBucketQueueCollection to the given Collection
	 * @param c Given Collection
	 * @return Number of elements drained
	 */
	public int drainTo(Collection<? super LeakyBucketQueueElement> c) {
		int result = 0;
		synchronized (_sync) {
			for (LeakyBucketQueue queue : _queues) {
				result += queue.drainTo(c);
			}
		}
		return result;
	}
	
	/**
	 * Producer
	 * Enqueue an element to the queue identified by the given associated Object.
	 * @param associatedObject Associated Object identifying the queue to enqueue to.
	 * @param element The element to be enqueued.
	 * @throws IllegalArgumentException if the associated Object doesn't 
	 * identify a valid Queue.
	 */
	public void enqueue(Object associatedObject, LeakyBucketQueueElement element) 
	throws IllegalArgumentException {
//		_logger.fine("enqueue(" + associatedObject + ", " + element + ")");
		synchronized (_sync) {
			LeakyBucketQueue queue = _queueMap.get(associatedObject);
			if (queue == null) {
				throw new IllegalArgumentException("No queue associated with object");
			}
			queue.add(element);
			_sync.notify();
		}
	}
	
	/**
	 * Consumer
	 * Dequeue an element from one of the queues in the Collection.  Blocks
	 * until element available.  Chooses which queue on a round-robin basis.
	 * @return The element from the Queue.
	 * @throws InterruptedException
	 */
	public LeakyBucketQueueElement dequeue() throws InterruptedException {
		LeakyBucketQueue queue = null;
		LeakyBucketQueueElement element = null;
		synchronized (_sync) {
			
			// Repeat until we have an element to return
			while (element == null) {
				int ix = 0;

				// Repeat until we have examined all queues or have found an element
				while (ix < _queues.size() && element == null) {
					
					try {
						queue = _queues.get(ix);
						switch (queue.getState()) {
						case DELAYED:
							// This queue is in delayed state.  Can't consume yet.
							break;
							
						default:
							// This queue is not frozen.
							if (queue.isElementReady()) {
								// Get element from this queue
								element = queue.get();
								if (element instanceof LeakyBucketQueueDelayElement) {
									// Element is a delay element.  Transition the
									// queue to DELAYED state and schedule transition
									// back to RUNNING state.
									final LeakyBucketQueue fQueue = queue;
									LeakyBucketQueueDelayElement delayElement =
										(LeakyBucketQueueDelayElement)element;
									element = null;
									queue.setState(LeakyBucketQueueState.DELAYED);
									_timer.schedule(
										new TimerTask() {

											@Override
											public void run() {
												synchronized (_sync) {
													fQueue.setState(LeakyBucketQueueState.RUNNING);
													_sync.notify();
													_timer.purge();
												}
											}
										},
										delayElement.getDelayMSecs()
									);
								
								}
							}						
						}
					} catch (IndexOutOfBoundsException e) {
						// This can happen; ignore it and move on to wait for
						// an enqueue.
						break;
					}
					
					// Advance to next queue
					ix++;
	
				} // Inner while ix < _queues.size && element == null
				
				// Examined all queues or found an element
				if (element == null) {
					// No item to dequeue found.  Wait for an enqueue.
					_sync.wait();
					ix = 0;			// Restart examination of queues
				}
				
			} // Outer while element == null
		} // synchronized (_sync)
		return element;
	}
	
	/**
	 * Get the name property of this LeakyBucketQueueCollection
	 * @return What I said
	 */
	public String getName() {
		return _name;
	}
	
	/**
	 * Set the name property of this LeakyBucketQueueCollection
	 * @param name Name
	 */
	public void setName(String name) {
		_name = name;
	}
	
	/**
	 * Dump this LeakyBucketQueueCollection.
	 * @param indent Amount of indentation
	 * @param detailed whether want verbose dump
	 * @return Dump
	 */
	public String dump(String indent, boolean detailed) {
		StringBuffer sb = new StringBuffer(
				indent + "LeakyBucketQueueCollection " + getName() + "\n");
		synchronized (_sync) {
			sb.append(indent + "  Current Index " + _currentIndex + "\n");
			if (detailed) {
				Set<Object> keys = _queueMap.keySet();
				for (Object key : keys) {
					LeakyBucketQueue q = _queueMap.get(key);
					sb.append(q.dump(indent + "  ", detailed));
				}
			}
		}
		return sb.toString();
	}
	
	/**
	 * A test program
	 * @param args Not used
	 */
	public static void main(String[] args) {
		// Start by doing a couple of simple operations
		final LeakyBucketQueueCollection qc =
			new LeakyBucketQueueCollection("LBQ1");
		
		Integer qLabel1 = new Integer(1);
		LeakyBucketQueue q1 = new LeakyBucketQueue("Q1", qLabel1);
		qc.add(q1);

		Integer qLabel2 = new Integer(2);
		LeakyBucketQueue q2 = new LeakyBucketQueue("Q2", qLabel2);
		qc.add(q2);

		LeakyBucketQueueDelayElement delay1 = new LeakyBucketQueueDelayElement(4000L);
		qc.enqueue(qLabel1, delay1);
		
		LeakyBucketQueueDelayElement delay2 = new LeakyBucketQueueDelayElement(8000L);
		qc.enqueue(qLabel2, delay2);
		
		TestQueueElement el1 = new TestQueueElement(1);
		qc.enqueue(qLabel1, el1);
		
		TestQueueElement el2 = new TestQueueElement(2);
		qc.enqueue(qLabel2, el2);
		
		// Now we'll do a more involved sequence to exercise.  We launch a
		// Thread to consume elements of the queue.  For 30 seconds, we'll
		// generate elements and enqueue them to a randomly chosen LeakyBucketQueue.
		// We'll randomly insert delay elements.  After 30 seconds, we'll stop
		// producing, and merely allow the consumer thread to consume the queue.
		try {
			LeakyBucketQueueElement el = qc.dequeue();
			System.out.println("Dequeued " + el);
			el = qc.dequeue();
			System.out.println("Dequeued " + el2);
			
			Thread thread = new Thread(
				new Runnable() {

					@Override
					public void run() {
						try {
							while (!Thread.interrupted()) {
								LeakyBucketQueueElement element = qc.dequeue();
								System.out.println("Dequeued " + element);
							}
						} catch (InterruptedException e) {
							// 
						}
					}
				}
			);
			thread.start();
			
			long t1 = System.currentTimeMillis();
			long t2 = System.currentTimeMillis();
			long elapsed = t2 - t1;
			int ix = 3;
			Random rng = new Random();
			
			while (elapsed < 30000L) {
				TestQueueElement element = new TestQueueElement(ix);
				LeakyBucketQueueDelayElement delayEl = null;
				if (rng.nextDouble() < 0.1d) {
					double delayFrac = rng.nextDouble();
					long delay = (long)(100.0d * delayFrac);
					delayEl = new LeakyBucketQueueDelayElement(delay);
				}
				if (rng.nextBoolean()) {
					if (delayEl != null) {
						qc.enqueue(qLabel1, delayEl);
					}
					qc.enqueue(qLabel1, element);
					
				} else {
					if (delayEl != null) {
						qc.enqueue(qLabel2, delayEl);
					}
					qc.enqueue(qLabel2, element);
				}
				ix++;
				t2 = System.currentTimeMillis();
				elapsed = t2 - t1;
				
				if (rng.nextDouble() < 0.1d) {
					long delay = (long)(rng.nextDouble() * 100.0d);
					Thread.sleep(delay);
				}
			}
			
			System.out.println("Done producing");
			System.out.println(qc.dump("", true));
			Thread.sleep(10000);
			
			System.out.println("Interrupting Consumer Thread");
			thread.interrupt();
			System.out.println("Waiting for Thread");
			thread.join(2000);
			System.out.println("DONE");
			
			System.out.println(qc.dump("", true));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Class used as queue element in test program
	 */
	public static class TestQueueElement implements LeakyBucketQueueElement {
		public int n;
		
		public TestQueueElement(int aN) {
			n = aN;
		}
		
		@Override
		public String dump(String indent, boolean detailed) {
			return indent + "  TestQueueElement " + n + "\n";
		}
		
		@Override
		public String toString() {
			return "TestQueueElement(" + n + ")";
		}
	}
}
