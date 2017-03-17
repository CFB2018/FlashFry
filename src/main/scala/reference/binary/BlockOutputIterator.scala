package reference.binary

import java.io.File

import bitcoding.{BitEncoding, BitPosition, StringCount}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.io.Source

/**
  * given an input file and a bin iterator, return blocks of guides and their genome positions as an array of longs
  */
class BlockOutputIterator(inputFile: File,
                          binIterator: Iterator[String],
                          bitEnc: BitEncoding,
                          posEnc: BitPosition) extends Iterator[BlockDescriptor] {

  // for each line in the sorted file, split and rerecord
  val input = Source.fromFile(inputFile).getLines()

  // store the growing block before returning it to the user
  var currentBlock: Option[Tuple2[Array[Long],Int]] = None

  // we have to look-ahead to see if the next guide is outside our current bin
  var nextGuide: Option[TargetPos] = None

  // where we get bins from
  val binIter = binIterator

  // the current bin we're in
  var currentBin : Option[String] = None

  // setup the first block (VERY IMPORTANT)
  loadNextBlock()

  /**
    * @return do we have
    */
  override def hasNext: Boolean = currentBlock.isDefined

  /**
    * @return the next block of the iterator -- can be a size 0 array
    */
  override def next(): BlockDescriptor = {
    val ret = BlockDescriptor(currentBin.get, currentBlock.getOrElse( (Array[Long](),0) )._1, currentBlock.getOrElse( (Array[Long](),0) )._2)
    loadNextBlock()
    ret
  }

  /**
    * performs the actual fetching of blocks
    */
  private def loadNextBlock() {

    if (!binIter.hasNext) {
      currentBlock = None
      return
    }
    currentBin = Some(binIter.next)

    val nextBinBuilder = mutable.ArrayBuilder.make[Long]
    var numberOfTargets = 0

    if (!(nextGuide.isDefined) && input.hasNext) {
      nextGuide = Some(BlockOutputIterator.lineToTargetAndPosition(input.next(), bitEnc, posEnc))
      numberOfTargets += 1
    }

    while (input.hasNext && nextGuide.isDefined && bitEnc.mismatchBin(currentBin.get, nextGuide.get.target) == 0) {
      val guide = BlockOutputIterator.lineToTargetAndPosition(input.next(), bitEnc, posEnc)

      if (bitEnc.mismatches(nextGuide.get.target, guide.target) == 0) {
        nextGuide = Some(guide.combine(nextGuide.get, bitEnc)) // combine off-targets
      } else {

        nextBinBuilder += nextGuide.get.target
        nextBinBuilder ++= nextGuide.get.positions
        nextGuide = Some(guide)
        numberOfTargets += 1
      }
    }

    // rare situation -- in the last block we need to write the guide
    if (nextGuide.isDefined && !(input.hasNext)) {
      nextBinBuilder += nextGuide.get.target
      nextBinBuilder ++= nextGuide.get.positions
      numberOfTargets += 1
      nextGuide = None
    }

    currentBlock = Some((nextBinBuilder.result(),numberOfTargets))
  }

}



object BlockOutputIterator {
  /**
    * convert a line into a target and position pair
    *
    * @param line   the line to split
    * @param bitEnc encodes targets
    * @param posEnc encodes positions
    * @return a case class representing a paired target and it's position
    */
  def lineToTargetAndPosition(line: String, bitEnc: BitEncoding, posEnc: BitPosition): TargetPos = {
    val sp = line.split("\t")

    TargetPos(bitEnc.bitEncodeString(StringCount(sp(3), 1)), Array[Long](posEnc.encode(sp(0), sp(1).toInt)))
  }
}



case class TargetPos(target: Long, positions: Array[Long]) {

  def combine(other: TargetPos, bitEnc: BitEncoding) = {
    assert(bitEnc.mismatches(other.target, target) == 0)
    TargetPos(target, positions ++ other.positions)
  }
}

case class BlockDescriptor(bin: String, block: Array[Long], numberOfTargets: Int)