package org.daselab.sparkel.unused

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.rdd._
import org.apache.spark.storage.StorageLevel
import org.apache.spark.HashPartitioner
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import java.net.URI
import org.apache.spark.broadcast.Broadcast
import scala.collection.mutable
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions

object SparkELHDFSTestCopy {

  var numPartitions = -1 // later initialized from command line
  var hashPartitioner: HashPartitioner = null

  /*
   * Initializes all the RDDs corresponding to each axiom-type. 
   * Note, we want all the typeAxioms to reside in cache() and not on disk, 
   * hence we are using the default persistence level, i.e. Memory_Only.
   */
  def initializeRDD(sc: SparkContext, dirPath: String) = {

    dummyStage(sc, dirPath)    
    val uAxioms = sc.textFile(dirPath + "sAxioms.txt")
                    .map[(Int, Int)](line => { line.split("\\|") match { case Array(x, a) => (a.toInt, x.toInt) }})
                    .partitionBy(hashPartitioner)
                    .setName("uAxioms")
                    .persist(StorageLevel.MEMORY_AND_DISK)
      
    uAxioms.count()

    val rAxioms: RDD[(Int, (Int, Int))] = sc.emptyRDD[(Int, (Int, Int))]

    val type1Axioms = sc.textFile(dirPath + "Type1Axioms.txt")
                        .map[(Int, Int)](line => {line.split("\\|") match { case Array(a, b) => (a.toInt, b.toInt)}})
                        .partitionBy(hashPartitioner)
                        .setName("type1Axioms")
                        .persist(StorageLevel.MEMORY_AND_DISK)
   
     type1Axioms.count()
      
     val uAxiomsFlipped = uAxioms.map({ case (a, x) => (x, a) })
                                 .partitionBy(hashPartitioner)
                                 .setName("uAxiomsFlipped")
                                 .persist(StorageLevel.MEMORY_AND_DISK)
                                 
     uAxiomsFlipped.count()
    
      
    val type2Axioms = sc.textFile(dirPath + "Type2Axioms.txt")
                        .map[((Int, Int), Int)](line => {line.split("\\|") match {
                            case Array(a1, a2, b) => ((a1.toInt, a2.toInt), b.toInt)
                              }
                         })
                        .partitionBy(hashPartitioner)
                        .setName("type2Axioms")
                        .persist(StorageLevel.MEMORY_AND_DISK)
                        
    type2Axioms.count()     
 
    val type2AxiomsConjunctsFlipped = type2Axioms.map({ case ((a1, a2), b) => ((a2, a1), b) })
                                     .partitionBy(hashPartitioner)
                                     .setName("type2AxiomsMap2")
                                     .persist(StorageLevel.MEMORY_AND_DISK)
                                     
    type2AxiomsConjunctsFlipped.count()

    val type3Axioms = sc.textFile(dirPath + "Type3Axioms.txt")
                        .map[(Int, (Int, Int))](line => {
                            line.split("\\|") match {
                            case Array(a, r, b) => (a.toInt, (r.toInt, b.toInt))
                            }
                         })
                        .partitionBy(hashPartitioner)
                        .setName("type3Axioms")
                        .persist(StorageLevel.MEMORY_AND_DISK)
                        
    type3Axioms.count()
      
    val type4Axioms = sc.textFile(dirPath + "Type4Axioms.txt")
                        .map[(Int, (Int, Int))](line => {
                            line.split("\\|") match {
                            case Array(r, a, b) => (a.toInt, (r.toInt, b.toInt))
                            }
                         })
                        .partitionBy(hashPartitioner)
                        .setName("type4Axioms")
                        .persist(StorageLevel.MEMORY_AND_DISK)    
    type4Axioms.count()     
    
    val type4AxiomsCompoundKey = type4Axioms.map({ case (a, (r, b)) => ((r, a), b) })
                                            .partitionBy(hashPartitioner)
                                            .setName("type4AxiomsCompoundKey")
                                            .persist(StorageLevel.MEMORY_AND_DISK)
    type4AxiomsCompoundKey.count()                                        
      
    val type5Axioms = sc.textFile(dirPath + "Type5Axioms.txt")
                        .map[(Int, Int)](line => {
                            line.split("\\|") match {
                            case Array(r, s) => (r.toInt, s.toInt)
                            }
                         })
                        .partitionBy(hashPartitioner)
                        .setName("type5Axioms")
                        .persist(StorageLevel.MEMORY_AND_DISK)
    type5Axioms.count()
      
    val type6Axioms = sc.textFile(dirPath + "Type6Axioms.txt")
                        .map[(Int, (Int, Int))](line => {
                            line.split("\\|") match {
                            case Array(r, s, t) => (r.toInt, (s.toInt, t.toInt))
                            }
                         })
                        .partitionBy(hashPartitioner)
                        .setName("type6Axioms")
                        .persist(StorageLevel.MEMORY_AND_DISK)
                        
     type6Axioms.count()                   

    //return the initialized RDDs as a Tuple object (can have at max 22 elements in Spark Tuple)
    (uAxioms, uAxiomsFlipped, rAxioms, type1Axioms, type2Axioms, type2AxiomsConjunctsFlipped, 
        type3Axioms, type4Axioms, type4AxiomsCompoundKey, type5Axioms, type6Axioms)
  }
  
  /**
   * Introduced a dummy stage to compensate for initial scheduling delay of 
   * the executors. This solves the issue of all-nothing data skew, i.e., all 
   * the partitions of an RDD are assigned to just one node. 
   */
  def dummyStage(sc: SparkContext, dirPath: String): Unit = {
    val sAxioms = sc.textFile(dirPath + "sAxioms.txt").map[(Int, Int)](line => { 
                                                          line.split("\\|") match { 
                                                          case Array(x, y) => (x.toInt, y.toInt) 
                                                          }})
                                                      .partitionBy(hashPartitioner)
                                                      .setName("sAxioms")
                                                      
      
     sAxioms.persist().count()
     sAxioms.unpersist().count()
  }
  
   //completion rule1
  def completionRule1(deltaUAxioms: RDD[(Int, Int)], type1Axioms: RDD[(Int, Int)], 
      loopCounter: Int): RDD[(Int, Int)] = {

    val r1Join = type1Axioms.join(deltaUAxioms)
                            .map({ case (k, v) => v })
                            .partitionBy(hashPartitioner)
                              
    r1Join
  }
  

  def completionRule2(loopCounter: Int, type2A1A2: Broadcast[Set[(Int, Int)]], 
      deltaUAxiomsFlipped: RDD[(Int, Int)], uAxiomsFlipped: RDD[(Int, Int)], type2Axioms: RDD[((Int, Int), Int)], 
      type2AxiomsConjunctsFlipped: RDD[((Int, Int), Int)]): RDD[(Int, Int)] = {
    
//    println("count of deltaUAxiomsFlipped: "+ deltaUAxiomsFlipped.count())
//    deltaUAxiomsFlipped.collect().foreach(println)
//      
//    println("count of uAxiomsFlipped: "+ uAxiomsFlipped.count())
//    uAxiomsFlipped.collect().foreach(println)
    
    //JOIN 1
    val r2Join1 = uAxiomsFlipped.join(deltaUAxiomsFlipped)
                                .setName("r2Join1_" + loopCounter)

//    println("count of r2Join1: "+ r2Join1.count()) 
//    r2Join1.collect().foreach(println)
    
    //filter joined uaxioms result before remapping for second join
    val r2JoinFilter = r2Join1.filter{ case (x, (a1, a2)) => type2A1A2.value.contains((a1, a2)) || type2A1A2.value.contains((a2, a1)) } //need the flipped combination for delta
                              .setName("r2JoinFilter_" + loopCounter) 
     
  
   
    //JOIN 2 - PART 1
    val r2JoinFilterMap = r2JoinFilter.map({ case (x, (a1, a2)) => ((a1, a2), x) })
                                      .partitionBy(hashPartitioner)
                                      .setName("r2JoinFilterMap_" + loopCounter)                                     
    val r2Join21 = r2JoinFilterMap.join(type2Axioms)
                                  .map({ case ((a1, a2), (x, b)) => (b, x) })
                                  .partitionBy(hashPartitioner)
                                  .setName("r2Join21_" + loopCounter)

   // println("count of r2Join21: "+ r2Join21.count())
    //JOIN 2 - PART 2
    val r2Join22 = r2JoinFilterMap.join(type2AxiomsConjunctsFlipped)
                                  .map({ case ((a1, a2), (x, b)) => (b, x) })
                                  .partitionBy(hashPartitioner)
                                  .setName("r2Join22_" + loopCounter)

    // println("count of r2Join22: "+ r2Join22.count())
    //UNION join results
    var r2Join2 = r2Join21.union(r2Join22)
                          .setName("r2Join2_" + loopCounter)

    r2Join2
  }
  
   //completion rule 3
  def completionRule3(deltUAxioms: RDD[(Int, Int)], type3Axioms: RDD[(Int, (Int, Int))]): RDD[(Int, (Int, Int))] = {
    val r3Join = type3Axioms.join(deltUAxioms)
    val r3Output = r3Join.map({ case (k, ((v1, v2), v3)) => (v1, (v3, v2)) })
                         .partitionBy(hashPartitioner)
                         
    r3Output

  }
  
  def completionRule4(filteredUAxioms: RDD[(Int, Int)], 
       rAxioms: RDD[(Int, (Int, Int))], 
       type4Axioms: RDD[(Int, (Int, Int))]): RDD[(Int, Int)] = { 
    val r4Join1 = type4Axioms.join(filteredUAxioms) 
           
    val r4Join1YKey = r4Join1.map({ case (a, ((r1, b), y)) => ((r1, y), b) })
                             .partitionBy(hashPartitioner)
    val rAxiomsPairYKey = rAxioms.map({ case (r2, (x, y)) => ((r2, y), x) })
                                 .partitionBy(hashPartitioner)
    val r4Join2 = r4Join1YKey.join(rAxiomsPairYKey)

    val r4Result = r4Join2.map({ case ((r, y), (b, x)) => (b, x) })
                          .partitionBy(hashPartitioner)
   
    r4Result
   }
  
  def completionRule4_delta(sc: SparkContext, 
      filteredDeltaUAxioms: RDD[(Int, Int)], filteredUAxioms: RDD[(Int, Int)], 
      filteredUAxiomsFlipped: RDD[(Int, Int)], 
      filteredDeltaRAxioms: RDD[(Int, (Int, Int))], 
      filteredRAxioms: RDD[(Int, (Int, Int))], 
      type4Axioms: RDD[(Int, (Int, Int))], 
      type4AxiomsCompoundKey: RDD[((Int, Int), Int)]): RDD[(Int, Int)] = { 
    
//    if(filteredDeltaUAxioms.isEmpty() && filteredDeltaRAxioms.isEmpty())
//      return sc.emptyRDD[(Int, Int)]
    
    // part-1 (using deltaU and fullR)
    val r4Join1P1 = type4Axioms.join(filteredDeltaUAxioms)            
    val r4Join1CompoundKey = r4Join1P1.map({ case (a, ((r1, b), y)) => ((r1, y), b) })
                                      .partitionBy(hashPartitioner)
    val rAxiomsPairYKey = filteredRAxioms.map({ case (r2, (x, y)) => ((r2, y), x) })
                                         .partitionBy(hashPartitioner)
    val r4Join2P1 = r4Join1CompoundKey.join(rAxiomsPairYKey)
    val r4Result1 = r4Join2P1.map({ case ((r, y), (b, x)) => (b, x) })
                             .partitionBy(hashPartitioner)
    
    // part-2 (using deltaR and fullU)                      
//    if(filteredDeltaRAxioms.isEmpty())
//      return r4Result1      
    val deltaRAxiomsYKey = filteredDeltaRAxioms.map({ case (r, (x, y)) => (y, (r, x)) })
                                               .partitionBy(hashPartitioner)           
    val r4Join1P2 = deltaRAxiomsYKey.join(filteredUAxiomsFlipped)
    val r4Join1P2CompoundKey = r4Join1P2.map({ case (y, ((r, x), a)) => ((r, a), x) })
                                        .partitionBy(hashPartitioner)
    val r4Join2P2 = r4Join1P2CompoundKey.join(type4AxiomsCompoundKey)
    val r4Result2 = r4Join2P2.map({ case ((r, a), (x, b)) => (b, x) })
                             .partitionBy(hashPartitioner)
                             
    val r4Result = r4Result1.union(r4Result2)                         
                                        
    r4Result
   }
  
  //completion rule 5
  def completionRule5(deltaRAxioms: RDD[(Int, (Int, Int))], 
      type5Axioms: RDD[(Int, Int)]): RDD[(Int, (Int, Int))] = {
    val r5Join = type5Axioms.join(deltaRAxioms)
                            .map({ case (k, (v1, (v2, v3))) => (v1, (v2, v3)) })
                            .partitionBy(hashPartitioner)

    r5Join
  }
  
  
  //completion rule 6
  def completionRule6_compoundKeys(sc: SparkContext, type6R1: Set[Int], type6R2: Set[Int], rAxioms: RDD[(Int, (Int, Int))], type6Axioms: RDD[(Int, (Int, Int))]): RDD[(Int, (Int, Int))] = {

    //filter rAxioms on r1 and r2 found in type6Axioms
    val rAxiomsFilteredOnR1 = rAxioms.filter{case (r1, (x, y)) => type6R1.contains(r1)}
    
    
    
    val rAxiomsFilteredOnR2 = rAxioms.filter{case (r2, (y, z)) => type6R2.contains(r2)}
                                     .map({ case (r2, (y, z)) => ((r2, y), z)}) //for r6Join2
                                     .partitionBy(hashPartitioner)
    
    
   
    //Join1 - joins on r                                
    val r6Join1 = type6Axioms.join(rAxiomsFilteredOnR1)
                             .map({ case (r1, ((r2, r3), (x, y))) => ((r2, y), (r3, x)) })
                             .partitionBy(hashPartitioner)

  
      
    //Join2 - joins on compound key
    val r6Join2 = r6Join1.join(rAxiomsFilteredOnR2) // ((r2,y),((r3,x),z))
                         .values // ((r3,x),z)
                         .map( { case ((r3,x),z) => (r3,(x,z))})
                         .partitionBy(hashPartitioner)
 
                        
      
   r6Join2
  }
  
  
  //completion rule 6
  def completionRule6_delta(sc: SparkContext, type6R1: Set[Int], type6R2: Set[Int], deltaRAxioms: RDD[(Int, (Int, Int))], rAxioms: RDD[(Int, (Int, Int))], type6Axioms: RDD[(Int, (Int, Int))]): RDD[(Int, (Int, Int))] = {

    //filter rAxioms on r1 and r2 found in type6Axioms
    val delRAxiomsFilterOnR1 = deltaRAxioms.filter{case (r1, (x, y)) => type6R1.contains(r1)}
    
     
    val r6Join11 = type6Axioms.join(delRAxiomsFilterOnR1)
                             .map({ case (r1, ((r2, r3), (x, y))) => ((r2, y), (r3, x)) })
                             .partitionBy(hashPartitioner)  
                             
//    if(r6Join11.isEmpty())
//        return sc.emptyRDD
    
    val rAxiomsFilterOnR2 = rAxioms.filter{case (r2, (y, z)) => type6R2.contains(r2)}
                                     .map({ case (r2, (y, z)) => ((r2, y), z)}) //for r6Join2
                                     .partitionBy(hashPartitioner)
          
    //Join2 - joins on compound key
    val r6Join12 = r6Join11.join(rAxiomsFilterOnR2) // ((r2,y),((r3,x),z))
                         .values // ((r3,x),z)
                         .map( { case ((r3,x),z) => (r3,(x,z))})
                         .partitionBy(hashPartitioner)
  
   //--------------------Reverse filtering of deltaR and rAxioms------------------------------------------------
   
   val delRAxiomsFilterOnR2 = deltaRAxioms.filter{case (r2, (x, y)) => type6R2.contains(r2)}
    
 //Join1 - joins on r 
   val type6AxiomsFlippedConjuncts = type6Axioms.map({case (r1,(r2,r3)) => (r2,(r1,r3))})
                                                 .partitionBy(hashPartitioner) 
    
   val r6Join21 = type6AxiomsFlippedConjuncts.join(delRAxiomsFilterOnR2)
                             .map({ case (r2,((r1,r3), (x, y))) => ((r1, y), (r3, x)) })
                             .partitionBy(hashPartitioner)
    
   val rAxiomsFilterOnR1 = rAxioms.filter{case (r1, (y, z)) => type6R2.contains(r1)}
                                     .map({ case (r1, (y, z)) => ((r1, y), z)}) //for r6Join2
                                     .partitionBy(hashPartitioner)
      
    //Join2 - joins on compound key
    val r6Join22 = r6Join21.join(rAxiomsFilterOnR1) // ((r1, y), ((r3, x),z))
                         .values // ((r3,x),z)
                         .map( { case ((r3,x),z) => (r3,(x,z))})
                         .partitionBy(hashPartitioner)
   
   val r6JoinFinal = r6Join12.union(r6Join22) 
                         
   r6JoinFinal
  }
  
  
  /**
   * For a hash partitioned RDD, it is sufficient to check for duplicate 
   * entries within a partition instead of checking them across the cluster. 
   * This avoids a shuffle operation. 
   */
  def customizedDistinctForUAxioms(rdd: RDD[(Int, Int)]): RDD[(Int, Int)] = {    
    val uAxiomsDeDup = rdd.mapPartitions ({
                        iterator => {
                           val axiomsSet = iterator.toSet
                           axiomsSet.iterator
                        }
                     }, true)                     
    uAxiomsDeDup                    
  }
  
  /**
   * similar to {@link #customizedDistinct(rdd: RDD[(Int, Int)])}
   */
  def customizedDistinctForRAxioms(rdd: RDD[(Int, (Int, Int))]): RDD[(Int, (Int, Int))] = {    
    val rAxiomsDeDup = rdd.mapPartitions ({
                        iterator => {
                           val axiomsSet = iterator.toSet
                           axiomsSet.iterator
                        }
                     }, true)                     
    rAxiomsDeDup                    
  }
  
  def customizedSubtractForUAxioms(rdd1: RDD[(Int, Int)], rdd2: RDD[(Int, Int)]): RDD[(Int, Int)] = {
    
    val groupByKeyRDD = rdd2.mapPartitions({
                 iterator => {
                   var groupByKeyMap: mutable.Map[Int, mutable.Set[Int]] = mutable.Map()
                   for((k, v) <- iterator) {
                     var mapValue = groupByKeyMap.get(k)
                     if(mapValue == None) {
                       var s: mutable.Set[Int] = mutable.Set(v)
                       groupByKeyMap += (k -> s)
                     }
                     else {
                       mapValue.get += v
                       groupByKeyMap += (k -> mapValue.get)
                     }
                   }
                   groupByKeyMap.iterator
                 }
                }, true)

    val leftOuterJoin = rdd1.leftOuterJoin(groupByKeyRDD)
    val diffRDD = leftOuterJoin.filter({ 
                                        case(x, (y, z)) => 
                                          if (z != None) !z.get.contains(y) 
                                          else true 
                                        })
                               .mapValues({ v => v._1 })
    diffRDD
  }
  
  def customizedSubtractForRAxioms(rdd1: RDD[(Int, (Int, Int))], 
      rdd2: RDD[(Int, (Int, Int))]): RDD[(Int, (Int, Int))] = {
    
    val groupByKeyRDD = rdd2.mapPartitions({
                 iterator => {
                   var groupByKeyMap: mutable.Map[Int, mutable.Set[(Int, Int)]] = mutable.Map()
                   for((k, v) <- iterator) {
                     var mapValue = groupByKeyMap.get(k)
                     if(mapValue == None) {
                       var s: mutable.Set[(Int, Int)] = mutable.Set(v)
                       groupByKeyMap += (k -> s)
                     }
                     else {
                       mapValue.get += v
                       groupByKeyMap += (k -> mapValue.get)
                     }
                   }
                   groupByKeyMap.iterator
                 }
                }, true)

    val leftOuterJoin = rdd1.leftOuterJoin(groupByKeyRDD)
    val diffRDD = leftOuterJoin.filter({ 
                                        case(x, (y, z)) => 
                                          if (z != None) !z.get.contains(y) 
                                          else true 
                                        })
                               .mapValues({ v => v._1 })
    diffRDD
  }

  //Deletes the existing output directory
  def deleteDir(dirPath: String): Boolean = {
    val localFileScheme = "file:///"
    val hdfsFileScheme = "hdfs://"
    val fileSystemURI: URI = {
      if (dirPath.startsWith(localFileScheme))
        new URI(localFileScheme)
      else if (dirPath.startsWith(hdfsFileScheme))
        new URI(hdfsFileScheme + "/")
      else
        null
    }
    // delete the output directory
    val hadoopConf = new Configuration()
    require(fileSystemURI != null, "Provide file:/// or hdfs:// for " +
      "input/output directories")
    val fileSystem = FileSystem.get(fileSystemURI, hadoopConf)
    fileSystem.delete(new Path(dirPath), true)
  }

  def prepareRule1Inputs(loopCounter: Int, sc: SparkContext, 
      uAxiomsFinal: RDD[(Int, Int)], prevDeltaURule1: RDD[(Int, Int)], 
      prevDeltaURule2: RDD[(Int, Int)], 
      prevDeltaURule4: RDD[(Int, Int)],prevPrevUAxiomsFinal: RDD[(Int, Int)]): RDD[(Int, Int)] = {
    
    var deltaUAxiomsForRule1 = {
    if (loopCounter == 1)
      uAxiomsFinal
    else
      sc.union(prevDeltaURule1, prevDeltaURule2, prevDeltaURule4)
//        .partitionBy(hashPartitioner) 
        .setName("deltaUAxiomsForRule2_" + loopCounter)
    
    }
  
    
 
    //add distinct                                              
    deltaUAxiomsForRule1 = customizedDistinctForUAxioms(deltaUAxiomsForRule1)
    
    if( loopCounter >1) {
      deltaUAxiomsForRule1 = customizedSubtractForUAxioms(deltaUAxiomsForRule1, 
                                                          prevPrevUAxiomsFinal)
      
//      deltaUAxiomsForRule1 = deltaUAxiomsForRule1.subtract(prevPrevUAxiomsFinal)
//                                               .partitionBy(hashPartitioner)
    }
    
    deltaUAxiomsForRule1  
  }
  
  def prepareRule2Inputs(loopCounter: Int, sc: SparkContext, 
      uAxiomsFinal: RDD[(Int, Int)], currDeltaURule1: RDD[(Int, Int)], 
      prevDeltaURule2: RDD[(Int, Int)], prevDeltaURule4: RDD[(Int, Int)], 
      uAxiomsFlipped: RDD[(Int, Int)], prevUAxiomsRule1: RDD[(Int, Int)]) = {
    var uAxiomsRule1 = uAxiomsFinal.union(currDeltaURule1)
    uAxiomsRule1 = customizedDistinctForUAxioms(uAxiomsRule1)
                          .setName("uAxiomsRule1_" + loopCounter)
     
    var deltaUAxiomsForRule2 = {
      if (loopCounter == 1)
        uAxiomsRule1 //uAxiom total for first loop
      else
        sc.union(currDeltaURule1, prevDeltaURule2, prevDeltaURule4)
//          .partitionBy(hashPartitioner) 
          .setName("deltaUAxiomsForRule2_" + loopCounter)
    }
  
    
    
    //add distinct
    deltaUAxiomsForRule2 = customizedDistinctForUAxioms(deltaUAxiomsForRule2)
    
     if(loopCounter > 1) { 
       deltaUAxiomsForRule2 = customizedSubtractForUAxioms(deltaUAxiomsForRule2, 
                                                           prevUAxiomsRule1)  
//       deltaUAxiomsForRule2 = deltaUAxiomsForRule2.subtract(prevUAxiomsRule1)
//                                                   .partitionBy(hashPartitioner)
    }
    
    //flip delta uAxioms
    var deltaUAxiomsFlipped = deltaUAxiomsForRule2.map({ case (a, x) => (x, a) }) 
                                                  .partitionBy(hashPartitioner)
                                                  .setName("deltaUAxiomsFlipped_" + loopCounter)
   
   //add distinct
   deltaUAxiomsFlipped = customizedDistinctForUAxioms(deltaUAxiomsFlipped) 

    var uAxiomsFlippedNew = uAxiomsRule1.map({case (a,x) => (x,a)})
                                        .partitionBy(hashPartitioner)
    uAxiomsFlippedNew = customizedDistinctForUAxioms(uAxiomsFlippedNew)
                              .setName("uAxiomsFlipped_" + loopCounter)
    (uAxiomsRule1, deltaUAxiomsForRule2, deltaUAxiomsFlipped, uAxiomsFlippedNew)                          
  }
  
  def prepareRule3Inputs(loopCounter: Int, sc: SparkContext, 
      uAxiomsRule1: RDD[(Int, Int)], currDeltaURule1: RDD[(Int, Int)], 
      currDeltaURule2: RDD[(Int, Int)], prevDeltaURule4: RDD[(Int, Int)], prevUAxiomsRule2: RDD[(Int, Int)]) = {
   
    var uAxiomsRule2 = uAxiomsRule1.union(currDeltaURule2)
    uAxiomsRule2 = customizedDistinctForUAxioms(uAxiomsRule2)
                              .setName("uAxiomsRule2_" + loopCounter)                             
    var deltaUAxiomsForRule3 = { 
       if (loopCounter == 1)
         uAxiomsRule2
       else
         sc.union(currDeltaURule1, currDeltaURule2, prevDeltaURule4)
//           .partitionBy(hashPartitioner) 
           .setName("deltaUAxiomsForRule3_" + loopCounter)
       }
    
    deltaUAxiomsForRule3 = customizedDistinctForUAxioms(deltaUAxiomsForRule3)
    
    if(loopCounter > 1) {
      deltaUAxiomsForRule3 = customizedSubtractForUAxioms(deltaUAxiomsForRule3, 
                                                          prevUAxiomsRule2)
//      deltaUAxiomsForRule3 = deltaUAxiomsForRule3.subtract(prevUAxiomsRule2)
//                                                 .partitionBy(hashPartitioner)
    }
    (uAxiomsRule2, deltaUAxiomsForRule3)
  }
  
  def prepareRule4Inputs(sc: SparkContext, loopCounter: Int, 
      currDeltaRRule3: RDD[(Int, (Int, Int))], 
      rAxiomsFinal: RDD[(Int, (Int, Int))], 
      type4FillersBroadcast: Broadcast[Set[Int]], 
      uAxiomsRule2: RDD[(Int, Int)]) = {
    var rAxiomsRule3 = {
      if(loopCounter == 1)
        currDeltaRRule3
      else
        rAxiomsFinal.union(currDeltaRRule3) //partitionAware union
      } 
    rAxiomsRule3 = customizedDistinctForRAxioms(rAxiomsRule3)
                                  .setName("rAxiomsRule3_" + loopCounter)     
       
     val filteredUAxiomsRule2 = { 
        if (type4FillersBroadcast != null)
          uAxiomsRule2.filter({ 
              case (k, v) => type4FillersBroadcast.value.contains(k) })
                      .partitionBy(hashPartitioner) 
        else
          sc.emptyRDD[(Int, Int)]
        }  
                             
      (rAxiomsRule3, filteredUAxiomsRule2)
  }
  
  def prepareDeltaRule4Inputs(sc: SparkContext, loopCounter: Int, 
      currDeltaRRule3: RDD[(Int, (Int, Int))], 
      rAxiomsFinal: RDD[(Int, (Int, Int))], 
      type4FillersBroadcast: Broadcast[Set[Int]], 
      deltaUAxiomsForRule3: RDD[(Int, Int)], 
      uAxiomsRule2: RDD[(Int, Int)], prevDeltaRRule5: RDD[(Int, (Int, Int))], 
        prevDeltaRRule6: RDD[(Int, (Int, Int))], 
        prevUAxiomsRule2: RDD[(Int, Int)], 
        prevRAxiomsRule3: RDD[(Int, (Int, Int))]) = {
    var rAxiomsRule3 = {
      if(loopCounter == 1)
        currDeltaRRule3
      else
        rAxiomsFinal.union(currDeltaRRule3) //partitionAware union
      } 
    rAxiomsRule3 = customizedDistinctForRAxioms(rAxiomsRule3)
                                  .setName("rAxiomsRule3_" + loopCounter) 
    
    var filteredCurrDeltaURule2 = { 
        if (type4FillersBroadcast != null)
          deltaUAxiomsForRule3.filter({ 
              case (a, x) => type4FillersBroadcast.value.contains(a) })
        else
          sc.emptyRDD[(Int, Int)]
      }  
      val filteredUAxiomsRule2 = { 
        if (type4FillersBroadcast != null)
            uAxiomsRule2.filter({ 
              case (a, x) => type4FillersBroadcast.value.contains(a) })
        else
          sc.emptyRDD[(Int, Int)]
      }
      // filtering on uAxiomsFlipped instead of flipping and partitioning on
      // filteredUAxiomsRule2 to avoid a shuffle operation (partitioning)
      val filteredUAxiomsFlippedRule2 = { 
        if (type4FillersBroadcast != null){
          uAxiomsRule2.map({case (a,x) => (x,a)})
                      .partitionBy(hashPartitioner)
                      .filter({ case (x, a) => type4FillersBroadcast.value.contains(a) })}
        else
          sc.emptyRDD[(Int, Int)]
      }
/*      
      val filteredCurrDeltaRRule3 = {
        if (type4RolesBroadcast != null)
          currDeltaRRule3.filter({ 
              case (r, (a, b)) => type4RolesBroadcast.value.contains(r) })
        else
          sc.emptyRDD[(Int, (Int, Int))]    
      }
      
      val filteredRAxiomsRule3 = {
        if (type4RolesBroadcast != null)
          rAxiomsRule3.filter({ 
              case (r, (a, b)) => type4RolesBroadcast.value.contains(r) })
        else
          sc.emptyRDD[(Int, (Int, Int))]    
      }
      currDeltaURule4 = completionRule4_delta(sc, filteredCurrDeltaURule2, 
          filteredUAxiomsRule2, filteredUAxiomsFlippedRule2, filteredCurrDeltaRRule3, 
          filteredRAxiomsRule3, type4Axioms, type4AxiomsCompoundKey)
*/
      var deltaRAxiomsToRule4 = { 
      if (loopCounter == 1)
        rAxiomsRule3
      else
        sc.union(prevDeltaRRule5, prevDeltaRRule6, currDeltaRRule3)
 //         .partitionBy(hashPartitioner)
      }
      //add distinct
      deltaRAxiomsToRule4 = customizedDistinctForRAxioms(deltaRAxiomsToRule4)
    
      // not using subtract on deltaU and deltaR for rule4 since it does not  
      // improve performance
//      filteredCurrDeltaURule2 = customizedSubtractForUAxioms(
//                                  filteredCurrDeltaURule2, prevUAxiomsRule2)
//      deltaRAxiomsToRule4 = customizedSubtractForRAxioms(
//                                  deltaRAxiomsToRule4, prevRAxiomsRule3)                            
      (rAxiomsRule3, filteredCurrDeltaURule2, filteredUAxiomsRule2, 
          filteredUAxiomsFlippedRule2, deltaRAxiomsToRule4)
  }
  
  def prepareRule5Inputs(loopCounter: Int, sc: SparkContext, 
      rAxiomsRule3: RDD[(Int, (Int, Int))], prevDeltaRRule6: RDD[(Int, (Int, Int))], prevDeltaRRule5: RDD[(Int, (Int, Int))], 
      currDeltaRRule3: RDD[(Int, (Int, Int))]): RDD[(Int, (Int, Int))] = {
    var deltaRAxiomsToRule5 = { 
      if (loopCounter == 1)
        rAxiomsRule3
      else
        sc.union(prevDeltaRRule5, prevDeltaRRule6, currDeltaRRule3)
 //         .partitionBy(hashPartitioner)
      }
    //add distinct
    deltaRAxiomsToRule5 = customizedDistinctForRAxioms(deltaRAxiomsToRule5)
    
    deltaRAxiomsToRule5
  }
  
  /*
   * The main method that inititalizes and calls each function corresponding to the completion rule 
   */
  def main(args: Array[String]): Unit = {
    if (args.length != 4) {
      System.err.println("Missing args:\n\t 0. input directory containing the axiom files \n\t" +
        "1. output directory to save the final computed sAxioms \n\t 2. Number of worker nodes in the cluster \n\t" +
        "3. Number of partitions (initial)")
      System.exit(-1)
    }

    val dirDeleted = deleteDir(args(1))
    numPartitions = args(3).toInt
    hashPartitioner = new HashPartitioner(numPartitions)
    val dirPath = args(0)
    
    //init time
    val t_init = System.nanoTime()

    val conf = new SparkConf().setAppName("SparkEL")
    conf.registerKryoClasses(Array(Class.forName("scala.collection.immutable.Set$EmptySet$"))) //to handle empty set distribution
    val sc = new SparkContext(conf)

    var (uAxioms, uAxiomsFlipped, rAxioms, type1Axioms, type2Axioms, 
        type2AxiomsConjunctsFlipped, type3Axioms, type4Axioms, type4AxiomsCompoundKey, 
        type5Axioms, type6Axioms) = initializeRDD(sc, dirPath)
      
     //  Thread.sleep(30000) //sleep for a minute  

    var prevUAxiomsCount: Long = 0
    var prevRAxiomsCount: Long = 0
    var currUAxiomsCount: Long = -1  //uAxioms.count
    var currRAxiomsCount: Long = -1  //rAxioms.count    
        
    println("Before closure computation. Initial uAxioms count: " + currUAxiomsCount + 
                                      ", Initial rAxioms count: " + currRAxiomsCount)

    
    var loopCounter: Int = 0

    var uAxiomsFinal = uAxioms
    var rAxiomsFinal = rAxioms

    var currDeltaURule1: RDD[(Int, Int)] = sc.emptyRDD
    var currDeltaURule2: RDD[(Int, Int)] = sc.emptyRDD
    var currDeltaURule4: RDD[(Int, Int)] = sc.emptyRDD
    
    var prevDeltaURule1: RDD[(Int, Int)] = sc.emptyRDD
    var prevDeltaURule2: RDD[(Int, Int)] = sc.emptyRDD
    var prevDeltaRRule3: RDD[(Int, (Int, Int))] = sc.emptyRDD
    var prevDeltaURule4: RDD[(Int, Int)] = sc.emptyRDD
    var prevDeltaRRule5: RDD[(Int, (Int, Int))] = sc.emptyRDD
    var prevDeltaRRule6: RDD[(Int, (Int, Int))] = sc.emptyRDD
    var prevUAxiomsFlipped = uAxiomsFlipped
    var prevUAxiomsFinal= uAxioms
    var prevRAxiomsFinal = rAxioms
    var prevPrevUAxiomsFinal: RDD[(Int, Int)] = sc.emptyRDD
    
    //for subtracting the input for rules
    var prevUAxiomsRule1: RDD[(Int, Int)] = sc.emptyRDD
    var prevUAxiomsRule2: RDD[(Int, Int)] = sc.emptyRDD
    var prevRAxiomsRule3: RDD[(Int, (Int, Int))] = sc.emptyRDD
    var prevUAxiomsRule4: RDD[(Int, Int)] = sc.emptyRDD
    var prevRAxiomsRule5: RDD[(Int, (Int, Int))] = sc.emptyRDD
    var prevRAxiomsRule6: RDD[(Int, (Int, Int))] = sc.emptyRDD
    
    
    

    //for pre-filtering for rule2
    val type2Collect = type2Axioms.collect()
    val type2FillersA1A2 = type2Collect.map({ case ((a1, a2), b) => (a1, a2) }).toSet
    val type2ConjunctsBroadcast = sc.broadcast(type2FillersA1A2)
    
    // used for filtering of uaxioms in rule4
    val type4Fillers = type4Axioms.collect().map({ case (a, (r, b)) => a }).toSet
    val type4FillersBroadcast = { 
      if (!type4Fillers.isEmpty)
        sc.broadcast(type4Fillers) 
      else 
        null
      }
    // used for filtering of raxioms in rule4
    val type4Roles = type4Axioms.collect().map({ case (a, (r, b)) => r }).toSet
    val type4RolesBroadcast = { 
      if (!type4Roles.isEmpty)
        sc.broadcast(type4Roles) 
      else 
        null
      }
    //bcast r1 and r2 for filtering in rule6
    val type6R1 = type6Axioms.collect()
                             .map({ case (r1, (r2, r3)) => r1})
                             .toSet
    
    val type6R1Bcast = sc.broadcast(type6R1)
    
    val type6R2 = type6Axioms.collect()
                             .map({ case (r1, (r2, r3)) => r2})
                             .toSet
   
    val type6R2Bcast = sc.broadcast(type6R2)
   //end of bcast for rule6  

    while (prevUAxiomsCount != currUAxiomsCount || prevRAxiomsCount != currRAxiomsCount) {
     
      var t_begin_loop = System.nanoTime()
      
      loopCounter += 1

      //Rule 1
      var deltaUAxiomsForRule1 = prepareRule1Inputs(loopCounter, sc, uAxiomsFinal, 
                                                     prevDeltaURule1, prevDeltaURule2, 
                                                     prevDeltaURule4, prevPrevUAxiomsFinal)
     
      var currDeltaURule1 = completionRule1(deltaUAxiomsForRule1, type1Axioms, loopCounter)
      
      //add distinct to output
      currDeltaURule1 = customizedDistinctForUAxioms(currDeltaURule1)
      println("----Completed rule1----")
      println("=====================================")
      
      //Rule2 
      
      var (uAxiomsRule1, deltaUAxiomsForRule2, deltaUAxiomsFlipped, 
          uAxiomsFlippedNew) = prepareRule2Inputs(
                                    loopCounter, sc, 
                                    uAxiomsFinal, currDeltaURule1, 
                                    prevDeltaURule2, prevDeltaURule4, 
                                    uAxiomsFlipped,prevUAxiomsRule1)
      uAxiomsFlipped = uAxiomsFlippedNew                   
      var currDeltaURule2 = completionRule2(loopCounter, type2ConjunctsBroadcast, 
          deltaUAxiomsFlipped, uAxiomsFlipped, type2Axioms, type2AxiomsConjunctsFlipped)
      
      //add distinct to output
      currDeltaURule2 = customizedDistinctForUAxioms(currDeltaURule2)
          
      println("----Completed rule2----")
      println("=====================================")
      
      //Rule3
      var (uAxiomsRule2, deltaUAxiomsForRule3) = prepareRule3Inputs(loopCounter, 
          sc, uAxiomsRule1, currDeltaURule1, currDeltaURule2, prevDeltaURule4, prevUAxiomsRule2)
     
      var currDeltaRRule3 = completionRule3(deltaUAxiomsForRule3, type3Axioms) 
      
      //add distinct to output
      currDeltaRRule3 = customizedDistinctForRAxioms(currDeltaRRule3)
                                       
      println("----Completed rule3----")
      
      //Rule4     
      var (rAxiomsRule3, filteredCurrDeltaURule2, filteredUAxiomsRule2, 
          filteredUAxiomsFlippedRule2, currDeltaRRule4) = prepareDeltaRule4Inputs(
              sc, loopCounter, currDeltaRRule3, rAxiomsFinal, type4FillersBroadcast, 
              deltaUAxiomsForRule3, uAxiomsRule2, prevDeltaRRule5, prevDeltaRRule6, 
              prevUAxiomsRule2, prevRAxiomsRule3)
      
/*                                                 
      var (rAxiomsRule3New, filteredUAxiomsRule2New) = prepareRule4Inputs(sc, 
          loopCounter, currDeltaRRule3, rAxiomsFinal, type4FillersBroadcast, uAxiomsRule2)
      rAxiomsRule3 = rAxiomsRule3New
      filteredUAxiomsRule2 = filteredUAxiomsRule2New
      currDeltaURule4 = completionRule4(filteredUAxiomsRule2, rAxiomsRule3, type4Axioms)
      //get delta U for only the current iteration  
      currDeltaURule4 = customizedSubtractForUAxioms(uAxiomsRule4, uAxiomsRule2)                                           
*/      
      currDeltaURule4 = completionRule4_delta(sc, filteredCurrDeltaURule2, 
          filteredUAxiomsRule2, filteredUAxiomsFlippedRule2, currDeltaRRule4, 
          rAxiomsRule3, type4Axioms, type4AxiomsCompoundKey)

      //add distinct to output
      currDeltaURule4 = customizedDistinctForUAxioms(currDeltaURule4)
      
      var uAxiomsRule4 = uAxiomsRule2.union(currDeltaURule4)
      uAxiomsRule4 = customizedDistinctForUAxioms(uAxiomsRule4)
                                     .setName("uAxiomsRule4_" + loopCounter) 
                                                                 
      println("\n----Completed rule4----")                               
      
      //Rule 5 
      val deltaRAxiomsToRule5 = currDeltaRRule4
      
      var currDeltaRRule5 = completionRule5(deltaRAxiomsToRule5, type5Axioms) 
      //add distinct to output
      currDeltaRRule5 = customizedDistinctForRAxioms(currDeltaRRule5)
      println("----Completed rule5----")
                   
      var rAxiomsRule5 = rAxiomsRule3.union(currDeltaRRule5)
      rAxiomsRule5 = customizedDistinctForRAxioms(rAxiomsRule5).setName("rAxiomsRule5_" + loopCounter) 
/*       
      var deltaRAxiomsToRule6 = {
         if(loopCounter == 1)
           rAxiomsRule5           
         else 
           sc.union(prevDeltaRRule6, currDeltaRRule3, currDeltaRRule5)
          // sc.union(currDeltaRRule3, currDeltaRRule5)  
//             .partitionBy(hashPartitioner)
       }
      
      deltaRAxiomsToRule6 = customizedDistinctForRAxioms(deltaRAxiomsToRule6)
       
      // var currDeltaRRule6 = completionRule6_delta(sc, type6R1Bcast.value, 
      //  type6R2Bcast.value, deltaRAxiomsToRule6 ,rAxiomsRule5, type6Axioms)
*/
       var currDeltaRRule6 = completionRule6_compoundKeys(sc, type6R1Bcast.value, 
            type6R2Bcast.value, rAxiomsRule5, type6Axioms)
       //add distinct to output
       currDeltaRRule6= customizedDistinctForRAxioms(currDeltaRRule6)
       println("----Completed rule6----")
       
       
       var rAxiomsRule6: RDD[(Int, (Int, Int))] = sc.emptyRDD
       rAxiomsRule6 = rAxiomsRule5.union(currDeltaRRule6)
//                                  .partitionBy(hashPartitioner)
       rAxiomsRule6 = customizedDistinctForRAxioms(rAxiomsRule6).setName("rAxiomsRule6_"+loopCounter)
      
      uAxiomsFinal = uAxiomsRule4
      rAxiomsFinal = rAxiomsRule6
      
      //testing placement of currDeltaURule4 persist before uAxiomsFinal persist. Does it cause any skew in task distribution? 
      currDeltaURule1 = currDeltaURule1.setName("currDeltaURule1_" + loopCounter)
                                       .persist(StorageLevel.MEMORY_AND_DISK)
      
      currDeltaURule4 = currDeltaURule4.setName("currDeltaURule4_" + loopCounter)
                                       .persist(StorageLevel.MEMORY_AND_DISK)
      
      currDeltaRRule3 = currDeltaRRule3.setName("currDeltaRRule3_"+loopCounter)
                                       .persist(StorageLevel.MEMORY_AND_DISK)
      
      currDeltaRRule5 = currDeltaRRule5.setName("currDeltaRRule5_" + loopCounter)
                                       .persist(StorageLevel.MEMORY_AND_DISK) 
                                       
      currDeltaRRule6 = currDeltaRRule6.setName("currDeltaRRule6_" + loopCounter)
                                       .persist(StorageLevel.MEMORY_AND_DISK)                                       
                                       
      uAxiomsRule1 = uAxiomsRule1.setName("prevuAxiomsRule1_"+loopCounter)
                                 .persist(StorageLevel.MEMORY_AND_DISK)     
                                 
      uAxiomsRule2 = uAxiomsRule2.setName("prevuAxiomsRule2_" + loopCounter)
                                 .persist(StorageLevel.MEMORY_AND_DISK)  
     
      uAxiomsFinal = uAxiomsFinal.setName("uAxiomsFinal_" + loopCounter)
                                 .persist(StorageLevel.MEMORY_AND_DISK)                          
                                 
      rAxiomsFinal = rAxiomsFinal.setName("rAxiomsFinal_"+loopCounter)
                                 .persist(StorageLevel.MEMORY_AND_DISK)
                                 
      
      //update counts
      prevUAxiomsCount = currUAxiomsCount
      prevRAxiomsCount = currRAxiomsCount
                                 
      var t_begin_uAxiomCount = System.nanoTime()
      currUAxiomsCount = uAxiomsFinal.count()
      var t_end_uAxiomCount = System.nanoTime()
      println("------Completed uAxioms count at the end of the loop: " + loopCounter + "--------")
      println("uAxiomCount: " + currUAxiomsCount + ", Time taken for uAxiom count: " + 
          (t_end_uAxiomCount - t_begin_uAxiomCount) / 1e9 + " s")
      println("====================================")
      
      
      var t_begin_rAxiomCount = System.nanoTime()
      currRAxiomsCount = rAxiomsFinal.count()
      var t_end_rAxiomCount = System.nanoTime()
      println("------Completed rAxioms count at the end of the loop: " + loopCounter + "--------")
      println("rAxiomCount: " + currRAxiomsCount + ", Time taken for rAxiom count: " + 
          (t_end_rAxiomCount - t_begin_rAxiomCount) / 1e9 + " s")
      println("====================================")
      
      
      
          
      //delta RDDs                                                                     
      currDeltaURule2 = currDeltaURule2.setName("currDeltaURule2_" + loopCounter)
                                       .persist(StorageLevel.MEMORY_AND_DISK)
                                       
      println("currDeltaURule2_" + loopCounter+": "+currDeltaURule2.count())
      
//      uAxiomsRule2 = uAxiomsRule2.setName("prevuAxiomsRule2"+loopCounter)
//                                 .persist(StorageLevel.MEMORY_AND_DISK)
                                 
//      println("uAxiomsRule2_" + loopCounter+": "+uAxiomsRule2.count())
      
      
      //prev delta RDDs assignments
      prevPrevUAxiomsFinal.unpersist()
      prevPrevUAxiomsFinal = prevUAxiomsFinal      
//      prevUAxiomsFinal.unpersist()
      prevUAxiomsFinal = uAxiomsFinal
      prevRAxiomsFinal.unpersist()
      prevRAxiomsFinal = rAxiomsFinal
      prevDeltaURule1.unpersist()
      prevDeltaURule1 = currDeltaURule1
      prevDeltaURule2.unpersist()                                      
      prevDeltaURule2 = currDeltaURule2
//      prevUAxiomsFlipped.unpersist()
//      prevUAxiomsFlipped = uAxiomsFlipped
      prevDeltaRRule3.unpersist()
      prevDeltaRRule3 = currDeltaRRule3
      prevDeltaURule4.unpersist()                                      
      prevDeltaURule4 = currDeltaURule4
      prevDeltaRRule5.unpersist()
      prevDeltaRRule5 = currDeltaRRule5
      prevDeltaRRule6.unpersist()
      prevDeltaRRule6 = currDeltaRRule6
      
      //for subtract operation for input of each rule
      prevUAxiomsRule1.unpersist()
      prevUAxiomsRule1 = uAxiomsRule1
      prevUAxiomsRule2.unpersist()
      prevUAxiomsRule2 = uAxiomsRule2
      
      var t_end_loop = System.nanoTime()
      
      println("Time for this loop: "+ (t_end_loop - t_begin_loop)/ 1e9+" s")
     
      println("Time until now: " + (t_end_loop - t_init)/1e9 +" s")

    }
   
     val t_end = System.nanoTime()
     println("#nodes\t#partitions\ttotal-runtime (in secs)")
     println(args(2) + "\t" + numPartitions + "\t" + (t_end - t_init)/1e9)
     
     
    // Thread.sleep(3000000) // add 100s delay for UI vizualization
    
    // commenting out sc.stop() to avoid IllegalStateException: RpcEnv already stopped
    // This could be related to https://issues.apache.org/jira/browse/SPARK-12967 
//    sc.stop()

  }

}