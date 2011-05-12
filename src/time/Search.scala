package time

import scala.concurrent.Lock
import scala.collection.mutable._

class SearchException(s:String,e:Throwable) extends RuntimeException(s,e) {
	def this() = this("",null)
	def this(s:String) = this(s,null)
	def this(e:Throwable) = this(null,e)
}

trait SearchState{
	def children:List[SearchState]
	def isEndState:Boolean
	def hasChildren:Boolean
	def cost:Double = 0.0
	def heuristic:Double = 0.0
	def assertEnqueueable:Boolean = true
	def assertDequeueable:Boolean = true
}

class Search[S <: SearchState : Manifest](store:Search.Store[S]){
	val storeLock = new Lock
	val SINK = new SearchState{
		override def children = List[SearchState]()
		override def isEndState = true
		override def hasChildren = true
		override def cost = java.lang.Double.POSITIVE_INFINITY
		override def heuristic:Double = 0
	}
	
	def search(
			start:S,
			result:(S,Int)=>Boolean,
			timeout:Int):Int = {
		//--Setup
		//(initialize store)
		storeLock.acquire
		store.clear
		store.enqueue(start)
		//(initialize loop)
		var shouldContinue:Boolean = true
		var count = 0
		//--Search
		while(shouldContinue && !store.isEmpty) {
			//(get head)
			val node:S = store.dequeue
			assert(node.assertDequeueable, ""+node+" is not dequeuable")
			count += 1
			//(return head if ok)
			if(node.isEndState){ shouldContinue = result(node, count) }
			//(timeout)
			if(count >= timeout){ shouldContinue = false }
			//(add children)
			if(shouldContinue){
				node.children.foreach( (s:SearchState) => {
					try{
						assert(s.assertEnqueueable, ""+s+" is not pushable")
						store.enqueue(s.asInstanceOf[S]) //add
					} catch {
						case (ex:ClassCastException) => 
							throw new SearchException("Child has bad type: " + s.getClass())
					}
				})
			}
		}
		//--Cleanup
		//(release store)
		storeLock.release
		//(return)
		count
	}
	
	def search(start:S,result:(S,Int)=>Boolean):Int
		= search(start,result,java.lang.Integer.MAX_VALUE)
	def search(start:S):Array[S] = {
		var results = List[S]()
		search(start, (result:S,count:Int) => {
					results = result :: results
					true
				})
		results.reverse.toArray
	}
	def search(start:S,maxSize:Int):Array[S] = {
		var results = List[S]()
		search(start, (result:S,count:Int) => {
					results = result :: results
					results.length < maxSize
				})
		results.reverse.toArray
	}
	def best(start:S):S = {
		var rtn:S = null.asInstanceOf[S]
		search(start, (result:S,count:Int) => {rtn = result; false})
		if(rtn == null){
			throw new SearchException("No solution")
		}
		rtn
	}
}



object Search {
	type Store[S <: SearchState] = {
		def enqueue(s:S*):Unit
		def dequeue():S
		def clear():Unit
		def isEmpty:Boolean
	}

	def apply[S <: SearchState : Manifest](s:Store[S]) = new Search[S](s)

	def cache[S <: SearchState](store:Store[S]):Store[S] = {
		new {
			val cache:Set[S] = new HashSet[S]
			var next:S = null.asInstanceOf[S]
			def enqueue(s:S*):Unit = { store.enqueue(s:_*) }
			def dequeue():S = {
				if(isEmpty) {
					throw new NoSuchElementException
				}
				val rtn = next
				next = null.asInstanceOf[S]
				cache += rtn
				rtn
			}
			def clear():Unit = { cache.clear; store.clear }
			def isEmpty:Boolean = {
				if(next != null){ return false } //have a next
				if(store.isEmpty){ return true } //underlying collection empty
				var cand:S = store.dequeue 
				while(cache contains (cand)){  //find an element not in cache
					if(store.isEmpty){ return true }
					cand = store.dequeue
				}
				next = cand //set that element
				false
			}
		}
	}

	def DFS[S <: SearchState]:Store[S] = new scala.collection.mutable.Stack[S]{
			def enqueue(s:S*):Unit = pushAll(s)
			def dequeue():S = pop()
		}
	def BFS[S <: SearchState]:Store[S] = new scala.collection.mutable.Queue[S]
	def UNIFORM_COST[S <: SearchState]:Store[S] = 
		new PriorityQueue[S]()(
			new Ordering[S]{
				def compare(a:S,b:S) = {
					if(a.cost < b.cost){ 1 }
					else if(a.cost > b.cost){ -1 }
					else { 0 }
				}
			})
	def A_STAR[S <: SearchState]:Store[S] =
		new PriorityQueue[S]()(
			new Ordering[S]{
				def compare(a:S,b:S) = {
					if((a.cost+a.heuristic) < (b.cost+b.heuristic)){ 1 }
					else if((a.cost+a.heuristic) > (b.cost+b.heuristic)){ -1 }
					else { 0 }
				}
			})
}




