/**
  * Copyright: [2011] Ben Lever
  */
package com.nicta.scoobi

import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.hadoop.mapred.FileAlreadyExistsException
import scala.collection.mutable.{Set => MSet, Map => MMap}

import MSCR._


/** Object for executing a Scoobi "plan". */
object Executor {


  /** The only state required to be passed around during execution of the
    * Scoobi compute graph.
    *
    * @param computeTable Which nodes have already been computed.
    * @param refCnts Number of nodes that are still to consume the output of an MSCR. */
  private class ExecState
      (val computeTable: MSet[AST.Node[_]],  // which nodes have been computed?
       val refCnts: MMap[AST.Node[_], Int])


  /** Entry-point: traverse the execution plan graph to produce each of the outputs. */
  def executePlan
      (mscrs: Set[MSCR],
       inputs: Set[InputStore],
       intermediates: Set[BridgeStore],
       outputs: Set[OutputStore]): Unit = {

    /* Check that all output dirs don't already exist. */
    def pathExists(p: Path) = {
      val s = FileSystem.get(Scoobi.conf).listStatus(p)
      if (s == null)          false
      else if (s.length == 0) false
      else                    true
    }

    outputs map (_.outputPath) find (pathExists(_)) match {
      case Some(p) => throw new FileAlreadyExistsException("Output " + p + " already exists.")
      case None    => Unit
    }


    /* Initialize execution state: Inputs are already computed (obviously), and
     * ref-counts begin at inital values. */
    val st = new ExecState(MSet.empty, MMap.empty)
    inputs.foreach { di => st.computeTable += di.node }
    intermediates.foreach { di => st.refCnts += (di.node -> di.refCnt) }

    /* Rumble over each output and execute their containing MSCR. Thread-through the
     * the execution state as it is updated. */
    outputs.foreach { out =>
      if (!st.computeTable.contains(out.node))
        executeMSCR(mscrs, st, containingMSCR(mscrs, out.node))
    }
  }


  /** Execute an MSCR. */
  private def executeMSCR(mscrs: Set[MSCR], st: ExecState, mscr: MSCR): Unit = {

    /* Make sure all inputs have been computed - recurse into executeMSCR. */
    mscr.inputNodes.foreach { input =>
      if (!st.computeTable.contains(input))
        executeMSCR(mscrs, st, containingMSCR(mscrs, input))
    }

    /* Make a Hadoop job and run it. */
    val job = MapReduceJob(mscr)
    job.run()

    /* Update compute table - all MSCR output nodes have now been produced. */
    mscr.outputNodes.foreach { node => st.computeTable += node }

    /* Update reference counts - decrement counts for all intermediates then
     * garbage collect any intermediates that have a zero reference count. */
    mscr.inputChannels.foreach { ic =>
      def updateRefCnt(node: AST.Node[_]) = {
        val rc = st.refCnts(node) - 1
        st.refCnts += (node -> rc)
        rc
      }

      ic match {
        case BypassInputChannel(bs@BridgeStore(n, _, _), _) => if (updateRefCnt(n) == 0) bs.freePath
        case MapperInputChannel(bs@BridgeStore(n, _, _), _) => if (updateRefCnt(n) == 0) bs.freePath
        case _                                               => Unit
      }
    }
  }
}
