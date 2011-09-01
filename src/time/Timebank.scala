package time

import java.util.Calendar
import java.util.{List => JList}

import scala.util.Sorting.quickSort
import scala.collection.JavaConversions._

import edu.stanford.nlp.util.CoreMap
import edu.stanford.nlp.ling.CoreLabel
import edu.stanford.nlp.ling.CoreAnnotation
import edu.stanford.nlp.ling.CoreAnnotations._

import org.joda.time.DateTime
import org.joda.time.Period
import org.joda.time.DateTimeZone

import org.goobs.database._
import org.goobs.stanford.CoreMapDatum
import org.goobs.testing.Dataset
import org.goobs.exec.Log._

//------------------------------------------------------------------------------
// UTILITIES
//------------------------------------------------------------------------------
// -- ENUMERATIONS --
object Language extends Enumeration {
	val english = Value
}
object NumberType extends Enumeration {
	val NONE, ORDINAL, NUMBER, UNIT = Value
}

// -- ANNOTATIONS --
class TimeExpressionsAnnotation extends CoreAnnotation[java.util.List[CoreMap]]{
	def getType:Class[java.util.List[CoreMap]] = classOf[java.util.List[CoreMap]]
}
class TimeValueAnnotation extends CoreAnnotation[Array[String]]{
	def getType:Class[Array[String]] = classOf[Array[String]]
}
class OriginalTimeTypeAnnotation extends CoreAnnotation[String]{
	def getType:Class[String] = classOf[String]
}
class OriginalTimeValueAnnotation extends CoreAnnotation[String]{
	def getType:Class[String] = classOf[String]
}
class TimeIdentifierAnnotation extends CoreAnnotation[String]{
	def getType:Class[String] = classOf[String]
}
class IsTestAnnotation extends CoreAnnotation[Boolean]{
	def getType:Class[Boolean] = classOf[Boolean]
}
class OriginalTokensAnnotation 
		extends CoreAnnotation[java.util.List[CoreLabel]]{
	def getType:Class[java.util.List[CoreLabel]] 
		= classOf[java.util.List[CoreLabel]]
}
class OriginalBeginIndexAnnotation
		extends CoreAnnotation[java.lang.Integer]{
	def getType:Class[java.lang.Integer] = classOf[java.lang.Integer]
}
class OriginalEndIndexAnnotation
		extends CoreAnnotation[java.lang.Integer]{
	def getType:Class[java.lang.Integer] = classOf[java.lang.Integer]
}



//------------------------------------------------------------------------------
// DATA
//------------------------------------------------------------------------------
class TimeDataset(data:Dataset[CoreMapDatum]) {
	def slice(minInclusive:Int,maxExclusive:Int):TimeDataset
		= new TimeDataset(data.slice(minInclusive,maxExclusive))

	def timexes:Array[Timex] = {
		start_track("Reading Timexes")
		//(variables)
		var rtn:List[Timex] = List[Timex]()
		var index = 0
		//(get timexes)
		data.iterator.foreach{ (doc:CoreMapDatum) => //for each doc
			val pubTime = Time(new DateTime(
				doc.get[Calendar,CalendarAnnotation](classOf[CalendarAnnotation])
				.getTimeInMillis))
			val sents = 
				doc.get[JList[CoreMap],SentencesAnnotation](
				classOf[SentencesAnnotation])
			sents.foreach{ (sent:CoreMap) => //for each sentence
				val tokens:JList[CoreLabel] =
					sent.get[JList[CoreLabel],TokensAnnotation](
					classOf[TokensAnnotation])
				assert(tokens != null, " No tokens for " + sent)
				val tokenList:List[CoreLabel] = tokens.map{ x => x }.toList
				val timexes:JList[CoreMap] = 
					sent.get[java.util.List[CoreMap],TimeExpressionsAnnotation](
					classOf[TimeExpressionsAnnotation])
				if(timexes != null){
					timexes.foreach{ (timex:CoreMap) =>  //for each timex
						val tmx = new Timex(index,timex,tokenList,pubTime)
						rtn = tmx :: rtn
						index += 1
						logG(tmx)
					}
				}
			}
		}
		end_track
		//(return)
		rtn.reverse.toArray
	}
}

class Timex(index:Int,time:CoreMap,sent:List[CoreLabel],pubTime:Time) {
	private val span:List[CoreLabel] = {
		val begin = time.get[java.lang.Integer,BeginIndexAnnotation](
							classOf[BeginIndexAnnotation])
		val end = time.get[java.lang.Integer,EndIndexAnnotation](
							classOf[EndIndexAnnotation])
		assert(end > begin, "Range is invalid: " + time)
		sent.slice(begin,end)
	}

	private def numType(str:String):NumberType.Value = {
		if(str != null){
			str match {
				case "ORDINAL" => NumberType.ORDINAL
				case "NUMBER" => NumberType.NUMBER
				case "UNIT" => NumberType.UNIT
				case _ => NumberType.NONE
			}
		} else {
			NumberType.NONE
		}
	}

	def tid:Int = index
	def words(test:Boolean):Array[Int] = { 
		span.map{ (lbl:CoreLabel) => 
			//(get annotation)
			val numVal = lbl.get[Number,NumericCompositeValueAnnotation](
				classOf[NumericCompositeValueAnnotation])
			val t = numType(lbl.get[String,NumericCompositeTypeAnnotation](
				classOf[NumericCompositeTypeAnnotation]))
			val w = if(t != NumberType.NONE){ numVal.toString } else { lbl.word }
			//(get word)
			if(test) {
				U.str2w(w, t) 
			} else {
				U.str2w(w, t)
			}
		}.toArray
	}
	def pos(test:Boolean):Array[Int] = { 
		span.map{ (lbl:CoreLabel) => 
			if(test) U.str2posTest(lbl.tag) else U.str2pos(lbl.tag)
		}.toArray
	}
	def nums:Array[Int] = { 
		span.map{ (lbl:CoreLabel) => 
			//(get annotation)
			val numVal = lbl.get[Number,NumericCompositeValueAnnotation](
				classOf[NumericCompositeValueAnnotation])
			val numType = lbl.get[String,NumericCompositeTypeAnnotation](
				classOf[NumericCompositeTypeAnnotation])
			//(get number)
			if(numVal != null){
				numVal.intValue
			} else {
				-1
			}
		}.toArray
	}
	def gold:Temporal
		= DataLib.array2JodaTime(
			time.get[Array[String],TimeValueAnnotation](classOf[TimeValueAnnotation]))
	
	def grounding:Time = pubTime

	override def toString:String 
		= "timex["+tid+"] "+words(false).map{ U.w2str(_) }.mkString(" * ")
//		= "timex["+tid+"] "+span.map{ _.word }.mkString(" * ")
}





//object Timebank {
//	
//}
//
//abstract class TimeDocument[A <: TimeSentence] extends org.goobs.testing.Datum{
//	@PrimaryKey(name="fid")
//	var fid:Int = 0
//	@Key(name="filename")
//	var filename:String = null
//	@Key(name="pub_time")
//	var pubTime:String = null
//	@Key(name="test")
//	var test:Boolean = false
//	@Key(name="notes")
//	var notes:String = null
//	def sentences:Array[A]
//	
//	def init:Unit = { 
//		refreshLinks; 
//		quickSort(sentences)( new Ordering[A] {
//			def compare(a:A,b:A) = a.compare(b)
//		}); 
//	}
//	def grounding:Time = Time(new DateTime(pubTime.trim))
//
//	override def getID = fid
//	override def toString:String = filename
//}
//
//
//
//abstract class TimeSentence extends DatabaseObject with Ordered[TimeSentence]{
//	@PrimaryKey(name="sid")
//	var sid:Int = 0
//	@Index
//	@Key(name="fid")
//	var fid:Int = 0
//	@Index
//	@Key(name="length")
//	var length:Int = 0
//	@Key(name="gloss")
//	var gloss:String = null
//
//	var document:TimeDocument[_<:TimeSentence] = null
//	
//	def tags:Array[_<:TimeTag]
//	def timexes:Array[_<:Timex]
//
//	var strings:Array[String] = null
//	private var words:Array[Int] = null
//	private var pos:Array[Int] = null
//	private var nums:Array[Int] = null
//	private var indexMap:Array[Int] = null
//	private var origIndexMap:Array[Int] = null
//
//	def wordSlice(begin:Int,end:Int):Array[Int] 
//		= words.slice(indexMap(begin),indexMap(end))
//	def posSlice(begin:Int,end:Int):Array[Int]
//		= pos.slice(indexMap(begin),indexMap(end))
//	def numSlice(begin:Int,end:Int):Array[Int]
//		= nums.slice(indexMap(begin),indexMap(end))
//	def origIndex(i:Int):Int
//		= origIndexMap(indexMap(i)) //TODO all this indexing is a godawful mess
//
//	def indexInDocument:Int = {
//		document.sentences.zipWithIndex.foreach{ case (s:TimeSentence,i:Int) =>
//			if(s.sid == this.sid){ return i }
//		}
//		throw new IllegalStateException(
//			"Could not find sentence in doc: "+
//				this.sid + ": " + this+
//				" ("+document.sentences.length+")")
//	}
//	
//	def init(doc:TimeDocument[_<:TimeSentence],	
//			str2w:(String,NumberType.Value)=>Int,str2pos:String=>Int):Unit = { 
//		refreshLinks; 
////		quickSort(timexes);  //TODO type hell breaks loose if uncommented
//		indexMap = (0 to length).toArray
//		origIndexMap = (0 to length).toArray
//		this.document = doc
//		//(tag variables)
//		val words = new Array[String](length)
//		val pos = new Array[String](length)
//		val numbers = new Array[Number](length)
//		val num_len = new Array[Int](length)
//		val num_type = new Array[NumberType.Value](length).map{x => NumberType.NONE}
//		//(get tags)
//		for( i <- 0 until tags.length ){
//			tags(i).key match {
//				case "form" =>
//					words(tags(i).wid-1) = tags(i).value
//				case "pos" =>
//					pos(tags(i).wid-1) = tags(i).value
//				case "num" => {
//					val isInt = """^(\-?[0-9]+)$""".r
//					val canInt = """^(\-?[0-9]+\.0+)$""".r
//					tags(i).value match {
//						case isInt(e) => numbers(tags(i).wid-1) = tags(i).value.toInt
//						case canInt(e) => 
//							numbers(tags(i).wid-1) = tags(i).value.toDouble.toInt
//						case _ => numbers(tags(i).wid-1) = tags(i).value.toDouble
//					}
//				}
//				case "orig" =>
//					origIndexMap(tags(i).wid-1) = tags(i).value.toInt
//				case "num_length" => 
//					num_len(tags(i).wid-1) = tags(i).value.toInt
//				case "num_type" => 
//					num_type(tags(i).wid-1) = NumberType.withName(tags(i).value)
//				case _ => 
//					//do nothing
//			}
//		}
//		//(process numbers)
//		if(O.collapseNumbers){
//			var wList = List[String]()
//			var pList = List[String]()
//			var nList = List[Number]()
//			var tList = List[NumberType.Value]()
//			var i:Int = 0
//			while(i < length){
//				if(numbers(i) != null){
//					wList = numbers(i).toString :: wList
//					pList = "CD" :: wList
//					nList = numbers(i) :: nList
//					tList = num_type(i) :: tList
//					(0 until num_len(i)).foreach{ (diff:Int) => 
//						indexMap(i+diff) = wList.length-1
//					}
//					i += num_len(i)
//				} else {
//					wList = words(i) :: wList
//					pList = pos(i) :: pList
//					nList = Int.MinValue :: nList
//					tList = num_type(i) :: tList
//					indexMap(i) = wList.length-1
//					i += 1
//				}
//			}
//			val numTypes:Array[NumberType.Value] = tList.reverse.toArray
//			this.nums  = nList.reverse.map( _.intValue ).toArray
//			this.strings = wList.reverse.toArray
//			this.words = strings.zipWithIndex.map{ case (str:String,i:Int) =>
//				assert(nums(i) == Int.MinValue || numTypes(i) != NumberType.NONE)
//				str2w(str,numTypes(i)) }.toArray
//			this.pos   = pList.reverse.map( str2pos(_) ).toArray
//			indexMap(length) = wList.length-1
//		} else {
//			this.strings = words
//			this.words = strings.zipWithIndex.map{ case (str:String,i:Int) =>
//				assert(nums(i) == Int.MinValue || num_type(i) != NumberType.NONE)
//				str2w(str,num_type(i)) }.toArray
//			this.pos   = pos.map( str2pos(_) )
//			this.nums  = words.map{ w => Int.MinValue }.toArray
//		}
//	}
//	
//	def bootstrap:(Array[String],Array[String]) = { 
//		refreshLinks; 
//		val words = new Array[String](length)
//		val pos = new Array[String](length)
//		for( i <- 0 until tags.length ){
//			tags(i).key match {
//				case "form" =>
//					words(tags(i).wid-1) = tags(i).value
//				case "pos" =>
//					pos(tags(i).wid-1) = tags(i).value
//				case _ => 
//					//do nothing
//			}
//		}
//		(words,pos)
//	}
//
//	override def compare(t:TimeSentence):Int = this.sid - t.sid
//	override def toString:String = gloss
//}
//
//class TimeTag extends DatabaseObject{
//	@Index
//	@Key(name="wid")
//	var wid:Int = 0
//	@Index
//	@Key(name="sid")
//	var sid:Int = 0
//	@Index
//	@Key(name="did")
//	var did:Int = 0
//	@Index
//	@Key(name="key")
//	var key:String = null
//	@Key(name="value")
//	var value:String = null
//}
//
//class Timex extends DatabaseObject with Ordered[Timex]{
//	@PrimaryKey(name="tid")
//	var tid:Int = 0
//	@Index
//	@Key(name="sid")
//	var sid:Int = 0
//	@Index
//	@Key(name="scope_begin")
//	var scopeBegin:Int = 0
//	@Index
//	@Key(name="scope_end")
//	var scopeEnd:Int = 0
//	@Index
//	@Key(name="type")
//	var timeType:String = null
//	@Key(name="value")
//	var timeVal:Array[String] = null
//	@Key(name="original_value")
//	var originalValue:String = null
//	@Key(name="handle")
//	var handle:String = null
//	@Key(name="gold_span")
//	var goldSpan:Array[String] = null
//	@Key(name="gloss")
//	var gloss:String = null
//
//	private var timeCache:Temporal = null
//	var grounding:Time = null
//	private var wordArray:Array[Int] = null
//	private var numArray:Array[Int] = null
//	private var posArray:Array[Int] = null
//
//	var sentence:TimeSentence = null
//	
//	def indexInDocument:Int = {
//		var i = 0
//		sentence.document.sentences.foreach{ case (s:TimeSentence) =>
//			s.timexes.foreach{ case (t:Timex) =>
//				if(t.tid == this.tid){ return i } else { i += 1 }
//			}
//		}
//		throw new IllegalStateException("Could not find timex in sentence")
//	}
//
//	def setWords(s:TimeSentence):Timex = {
//		//(set variables)
//		sentence = s
//		wordArray = s.wordSlice(scopeBegin-1,scopeEnd-1)
//		numArray = s.numSlice(scopeBegin-1,scopeEnd-1)
//		posArray = s.posSlice(scopeBegin-1,scopeEnd-1)
//		//(ensure correctness)
//		wordArray.zipWithIndex.foreach{ case (w:Int,i:Int) =>
//			if(U.isNum(w) && numArray(i) == Int.MinValue){
//				if(O.todoHacks && numArray(i) == Int.MinValue){ 
//					//TODO fix in NumberNormalizer
//					if(U.isInt(s.strings(scopeBegin-1+i))){
//						numArray(i) = U.str2int(s.strings(scopeBegin-1+i))
//					} else {
//						numArray(i) = 0 //TODO double hax
//					}
//				}
//				if(numArray(i) == Int.MinValue){
//					println(U.join(wordArray.map{ U.w2str(_) }," "))
//					println(U.join(numArray," "))
//					throw new IllegalStateException("Number not recognized; sid: "+s.sid)
//				}
//			}
//		}
//		//(return)
//		this
//	}
//	def words:Array[Int] = wordArray
//	def pos:Array[Int] = posArray
//	def nums:Array[Int] = numArray
//	def ground(t:Time):Timex = { grounding = t; this }
//	def gold:Temporal = {
//		if(timeCache == null){
//			assert(timeVal.length > 0, "No time value for timex " + tid + "!")
//			val inType:String = timeVal(0).trim
//			timeCache = inType match {
//				case "INSTANT" => {
//					//(case: instant time)
//					assert(timeVal.length == 2, "Instant has one element")
//					if(timeVal(1).trim == "NOW"){
//						Range(Duration.ZERO)
//					} else {
//						Range(new DateTime(timeVal(1).trim))
//					}
//				}
//				case "RANGE" => {
//					//(case: range)
//					assert(timeVal.length == 3, "Range has two elements")
//					val begin:String = timeVal(1).trim
//					val end:String = timeVal(2).trim
//					if(begin == "x" || end == "x"){
//						//(case: unbounded range)
//						if(begin == "x") assert(end == "NOW", "assumption")
//						if(end == "x") assert(begin == "NOW", "assumption")
//						if(begin == "x"){
//							new PartialTime( (r:Range) => r cons Lex.REF )
//						} else if(end == "x"){
//							new PartialTime( (r:Range) => Lex.REF cons r )
//						} else {
//							throw fail("Should not reach here")
//						}
//					} else {
//						//(case: normal range)
//						Range( new DateTime(begin), new DateTime(end) )
//					}
//				}
//				case "PERIOD" => {
//					//(case: duration)
//					assert(timeVal.length == 8, "Period has invalid element count")
//					var isApprox = false
//					def mkInt(str:String):Int = {
//						if(str.equalsIgnoreCase("x")){ isApprox = true; 1 }else{ str.toInt }
//					}
//					val rtn:Duration = Duration(new Period(
//						mkInt(timeVal(1)),
//						mkInt(timeVal(2)),
//						mkInt(timeVal(3)),
//						mkInt(timeVal(4)),
//						mkInt(timeVal(5)),
//						mkInt(timeVal(6)),
//						mkInt(timeVal(7)),
//						0
//						))
//					if(isApprox){ ~rtn } else { rtn }
//				}
//				case "UNK" => {
//					new UnkTime
//				}
//				case _ => throw new IllegalStateException("Unknown time: " + 
//					inType + " for timex: " + this)
//			}
//		}
//		timeCache
//	}
//
//	override def compare(t:Timex):Int = this.tid - t.tid
//	override def toString:String = {
//		"" + tid + "["+scopeBegin+"-"+scopeEnd+"]: " +
//		{ if(this.wordArray==null) gloss.replaceAll("""\n+"""," ") 
//		  else U.join(this.wordArray.map( U.w2str(_) )," ") }
//	}
//	override def equals(other:Any):Boolean = {
//		return other.isInstanceOf[Timex] && other.asInstanceOf[Timex].tid == tid
//	}
//	override def hashCode:Int = tid
//}
//
//@Table(name="timebank_tlink")
//class TLink extends DatabaseObject with Ordered[TLink]{
//	@PrimaryKey(name="lid")
//	private var lid:Int = 0
//	@Key(name="fid")
//	private var fid:Int = 0
//	@Key(name="source")
//	private var sourceTimexId:Int = 0
//	@Key(name="target")
//	private var targetTimexId:Int = 0
//	@Key(name="type")
//	private var linkType:String = null
//
//	override def compare(t:TLink):Int = this.lid - t.lid
//	override def toString:String =""+sourceTimexId+"-"+linkType+"->"+targetTimexId
//	override def equals(o:Any):Boolean = {
//		if(o.isInstanceOf[TLink]){
//			val other:TLink = o.asInstanceOf[TLink]
//			other.lid == this.lid
//		}else{
//			false
//		}
//	}
//	override def hashCode:Int = lid
//}
//
////-- TIMEBANK --
//@Table(name="timebank_doc")
//class TimebankDocument extends TimeDocument[TimebankSentence] {
//	@Child(localField="fid", childField="fid")
//	var sentencesVal:Array[TimebankSentence] = null
//	override def sentences = sentencesVal
//}
//@Table(name="timebank_sent")
//class TimebankSentence extends TimeSentence {
//	@Child(localField="sid", childField="sid")
//	private var tagsVal:Array[TimebankTag] = null
//	@Child(localField="sid", childField="sid")
//	var timexesVal:Array[TimebankTimex] = null
//	override def tags = tagsVal
//	override def timexes = timexesVal
//}
//@Table(name="timebank_tag")
//class TimebankTag extends TimeTag
//@Table(name="timebank_timex")
//class TimebankTimex extends Timex
//
////-- TEMPEVAL ENGLISH --
//@Table(name="tempeval_english_doc")
//class EnglishDocument extends TimeDocument[EnglishSentence] {
//	@Child(localField="fid", childField="fid")
//	var sentencesVal:Array[EnglishSentence] = null
//	override def sentences = sentencesVal
//}
//@Table(name="tempeval_english_sent")
//class EnglishSentence extends TimeSentence {
//	@Child(localField="sid", childField="sid")
//	private var tagsVal:Array[EnglishTag] = null
//	@Child(localField="sid", childField="sid")
//	var timexesVal:Array[EnglishTimex] = null
//	override def tags = tagsVal
//	override def timexes = timexesVal
//}
//@Table(name="tempeval_english_tag")
//class EnglishTag extends TimeTag
//@Table(name="tempeval_english_timex")
//class EnglishTimex extends Timex
//
//
//
////-- NYT GUTIME --
//@Table(name="gutime_nyt_doc")
//class GUTimeNYTDocument extends TimeDocument[GUTimeNYTSentence] {
//	@Child(localField="fid", childField="fid")
//	var sentencesVal:Array[GUTimeNYTSentence] = null
//	override def sentences = sentencesVal
//}
//@Table(name="gutime_nyt_sent")
//class GUTimeNYTSentence extends TimeSentence {
//	@Child(localField="sid", childField="sid")
//	private var tagsVal:Array[GUTimeNYTTag] = null
//	@Child(localField="sid", childField="sid")
//	var timexesVal:Array[GUTimeNYTTimex] = null
//	override def tags = tagsVal
//	override def timexes = timexesVal
//}
//@Table(name="gutime_nyt_tag")
//class GUTimeNYTTag extends TimeTag
//@Table(name="gutime_nyt_timex")
//class GUTimeNYTTimex extends Timex
