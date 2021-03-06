package org.daselab.sparkel.unused


import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.rdd._
import org.apache.spark.storage.StorageLevel
import main.scala.org.daselab.sparkel.Constants._
import org.apache.spark.util.SizeEstimator
import java.net.URI
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.fs.Path
import org.apache.spark.HashPartitioner
import scala.collection.immutable.Set
import scala.reflect.ClassTag
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions

object SparkELAlgoOpt{
  
  private var hashPartitioner: HashPartitioner = null
//  private var numPartitions = -1
  private val conf = new SparkConf().setAppName("SparkELAlgoOpt")
  private val sc = new SparkContext(conf)
  
  /*
   * Initializes all the RDDs corresponding to each axiom-type. 
   */
  def initializeRDD(sc: SparkContext, dirPath: String) = {
    val uAxioms = sc.textFile(dirPath + "sAxioms.txt").map[(Int, Int)](line => { 
      line.split("\\|") match { case Array(x, y) => (y.toInt, x.toInt) } })
      .partitionBy(hashPartitioner).persist(StorageLevel.MEMORY_AND_DISK)
    val rAxioms: RDD[(Int, (Int, Int))] = sc.emptyRDD
    val type1Axioms = sc.textFile(dirPath + "Type1Axioms.txt")
                      .map[(Int, Int)](line => { line.split("\\|") match { 
                        case Array(x, y) => (x.toInt, y.toInt) } })
                      .partitionBy(hashPartitioner).persist()
    val type2Axioms = sc.textFile(dirPath + "Type2Axioms.txt")
                      .map[(Int, (Int, Int))](line => { line.split("\\|") match { 
                        case Array(x, y, z) => (x.toInt, (y.toInt, z.toInt)) } })
                      .partitionBy(hashPartitioner).persist()
    val type3Axioms = sc.textFile(dirPath + "Type3Axioms.txt")
                      .map[(Int, (Int, Int))](line => { line.split("\\|") match { 
                        case Array(x, y, z) => (x.toInt, (y.toInt, z.toInt)) } })
                      .partitionBy(hashPartitioner).persist()
    val type4Axioms = sc.textFile(dirPath + "Type4Axioms.txt")
                      .map[(Int, (Int ,Int))](line => { line.split("\\|") match { 
                        case Array(x, y, z) => (x.toInt, (y.toInt, z.toInt)) } })
                      .partitionBy(hashPartitioner).persist()
    val type5Axioms = sc.textFile(dirPath + "Type5Axioms.txt")
                      .map[(Int, Int)](line => { line.split("\\|") match { 
                        case Array(x, y) => (x.toInt, y.toInt) } })
                      .partitionBy(hashPartitioner).persist()
    val type6Axioms = sc.textFile(dirPath + "Type6Axioms.txt")
                      .map[(Int, (Int, Int))](line => { line.split("\\|") match { 
                        case Array(x, y, z) => (x.toInt, (y.toInt, z.toInt)) } })
                      .partitionBy(hashPartitioner).persist()

    //return the initialized RDDs as a Tuple object (can at max have 22 elements in Spark Tuple)
    (uAxioms, rAxioms, type1Axioms, type2Axioms, type3Axioms, type4Axioms, type5Axioms, type6Axioms)
  }

  //completion rule1
  def completionRule1(uAxioms: RDD[(Int, Int)], 
      type1Axioms: RDD[(Int, Int)]): RDD[(Int, Int)] = {

    val r1Join = type1Axioms.join(uAxioms).map({ case (k, v) => v })
                            .partitionBy(type1Axioms.partitioner.get) 
                            .persist()
   // val uAxiomsNew = uAxioms.union(r1Join).distinct // uAxioms is immutable as it is input parameter

    r1Join
  }

  def completionRule2_deltaNew(type2A1A2: Set[(Int,Int)], deltaUAxioms: RDD[(Int, Int)], 
      uAxioms: RDD[(Int, Int)], type2Axioms: RDD[(Int, (Int, Int))]): RDD[(Int, Int)] = {        
    println("DeltaU Filtered Self-join version!!")   
    //flip the uAxioms for self join on subclass 
    val uAxiomsFlipped = uAxioms.map({case (a, x) => (x, a)})   
    //for delta version
    val deltaUAxiomsFlipped = deltaUAxioms.map({case (a, x) => (x, a)})
    val r2Join1 = uAxiomsFlipped.join(deltaUAxiomsFlipped)
                                .partitionBy(type2Axioms.partitioner.get)
    val r2JoinFilter = r2Join1.filter{ case (x, (a1, a2)) => 
      type2A1A2.contains((a1, a2)) || type2A1A2.contains((a2, a1)) } //need the flipped combination for delta
    val r2JoinFilterMap = r2JoinFilter.map({case (x, (a1, a2)) => ((a1, a2), x)})
                                      .partitionBy(type2Axioms.partitioner.get)
    var type2AxiomsMap = type2Axioms.map({case(a1, (a2, b)) => ((a1, a2), b)})
                                    .partitionBy(type2Axioms.partitioner.get)
    val r2Join21 = r2JoinFilterMap.join(type2AxiomsMap)
                                  .map({case ((a1, a2), (x, b)) => (b, x)})
                                  .partitionBy(type2Axioms.partitioner.get)
    type2AxiomsMap = type2Axioms.map({case(a1, (a2, b)) => ((a2, a1), b)})
                                .partitionBy(type2Axioms.partitioner.get)
    val r2Join22 = r2JoinFilterMap.join(type2AxiomsMap)
                                  .map({case ((a1, a2), (x, b)) => (b, x)})
                                  .partitionBy(type2Axioms.partitioner.get)    
    val r2Join2 = r2Join21.union(r2Join22)
                          .distinct
                          .partitionBy(type2Axioms.partitioner.get) 
                          .persist()
    r2Join2
  }
  
  def completionRule2_selfJoin(type2A1A2: Set[(Int,Int)], uAxioms: RDD[(Int, Int)], 
      type2Axioms: RDD[(Int, (Int, Int))], numPartitions: Int): RDD[(Int, Int)] = {

    println("Filtered Self-join version!!")
    val uAxiomsFlipped = uAxioms.map({case (a,x) => (x,a)})
    
//    var t_begin = System.nanoTime()
    val r2Join1 = uAxiomsFlipped.join(uAxiomsFlipped, numPartitions)
//    val r2Join1_count = r2Join1.persist(StorageLevel.MEMORY_ONLY_SER).count()
//    var t_end = System.nanoTime()
//    println("r2Join1: uAxiomsFlipped.join(uAxiomsFlipped). Count= " +r2Join1_count+ 
//        ", Time taken: "+(t_end - t_begin) / 1e6 + " ms")
 
    val r2JoinFilter = r2Join1.filter{ case (x, (a1,a2)) => type2A1A2.contains((a1,a2))}
//    println("!!!!!!!Filtered r2Join1 before second join: r2Join1Map.filter(). Count= " + r2JoinFilter.count)
    
//    var t_begin = System.nanoTime()
    val r2JoinFilterMap = r2JoinFilter.map({case (x, (a1,a2)) => ((a1,a2),x)})
    val type2AxiomsMap = type2Axioms.map({case(a1,(a2,b)) => ((a1,a2),b)})
    val r2Join2 = r2JoinFilterMap.join(type2AxiomsMap)
                                 .map({case ((a1,a2),(x,b)) => (b,x)})
                                 .partitionBy(type2Axioms.partitioner.get)
//    val r2Join2_count = r2Join2.count
//    var t_end = System.nanoTime()
//    println("r2Join2:  Count= "+r2Join2_count+", Time taken: "+(t_end - t_begin) / 1e6 + " ms")
    
    r2Join2
  }
  
  //completion rule 2
  def completionRule2(deltaUAxioms: RDD[(Int, Int)], uAxioms: RDD[(Int, Int)], 
      type2Axioms: RDD[(Int, (Int, Int))], iterationCount: Int): RDD[(Int, Int)] = {
    
    //for all iterations, non-delta version of uaxioms are used
    if (iterationCount >= 1) {
      println("--------Type2 Joins Iteration 1--------")
      var t_begin = System.nanoTime()
      val r2Join1 = type2Axioms.join(uAxioms)
      val r2Join1Remapped = r2Join1.map({ case (k, ((v1, v2), v3)) => (v1, (v2, v3)) })
      val r2Join2 = r2Join1Remapped.join(uAxioms)
      val r2JoinOutput = r2Join2.filter({ case (k, ((v1, v2), v3)) => v2 == v3 })
                                .map({ case (k, ((v1, v2), v3)) => (v1, v2) })
                                .distinct
                                .partitionBy(type2Axioms.partitioner.get)
      val r2JoinOutputCount = r2JoinOutput.count()                          
      var t_end = System.nanoTime()      
      println("r2JoinOutputCount: " + r2JoinOutputCount + 
          " Time taken: " + (t_end - t_begin)/1e6 + " ms")
      r2JoinOutput                          
    }
    else {
      println("--------Type2 Joins--------")
      var t_begin = System.nanoTime()
      val r2Join1 = type2Axioms.join(deltaUAxioms)
                               .map({ case (a1, ((a2, b), x)) => (a2, (b, x)) })
      val r2Join1Count = r2Join1.count()
      var t_end = System.nanoTime()      
      println("Delta1 -- type2Axioms.join(deltaUAxioms): " + r2Join1Count + 
          " Time taken: " + (t_end - t_begin)/1e6 + " ms")
          
      t_begin = System.nanoTime()    
      val r2Join2 = r2Join1.join(uAxioms)
      val r2Join2Count = r2Join2.count()
      t_end = System.nanoTime() 
      println("Delta1 -- second join on uAxioms: " + r2Join2Count + 
          " Time taken: " + (t_end - t_begin)/1e6 + " ms")
      
      t_begin = System.nanoTime()    
      val r2Output1 = r2Join2.filter({ case (a2, ((b, x1), x2)) => x1 == x2 })
                             .map({ case (a2, ((b, x1), x2)) => (b, x1) })
      val r2Output1Count = r2Output1.count()
      t_end = System.nanoTime() 
      println("Delta1 -- r2Output1: " + r2Output1Count + 
          " Time taken: " + (t_end - t_begin)/1e6 + " ms")
      
      t_begin = System.nanoTime()
      val type2AxiomsA2Key = type2Axioms.map({ case (a1, (a2, b)) => (a2, (a1, b)) })
      val r2JoinA2 = type2AxiomsA2Key.join(deltaUAxioms)
                                     .map({ case (a2, ((a1, b), x)) => (a1, (b, x)) })
      val r2JoinA2Count = r2JoinA2.count()
      t_end = System.nanoTime() 
      println("Delta2 -- type2Axioms.join(deltaUAxioms): " + r2JoinA2Count + 
          " Time taken: " + (t_end - t_begin)/1e6 + " ms")  
      
      t_begin = System.nanoTime()    
      val r2JoinA1 = r2JoinA2.join(uAxioms)
      val r2JoinA1Count = r2JoinA1.count()
      t_end = System.nanoTime() 
      println("Delta2 -- second join on uAxioms: " + r2JoinA1Count + 
          " Time taken: " + (t_end - t_begin)/1e6 + " ms")
      
      t_begin = System.nanoTime()      
      val r2Output2 = r2JoinA1.filter({ case (a1, ((b, x1), x2)) => x1 == x2 })
                              .map({ case (a1, ((b, x1), x2)) => (b, x1) })
      val r2Output2Count = r2Output2.count()
      t_end = System.nanoTime() 
      println("Delta2 -- r2Output2: " + r2Output2Count + 
          " Time taken: " + (t_end - t_begin)/1e6 + " ms")    
      
      t_begin = System.nanoTime()     
      val r2Output = r2Output1.union(r2Output2)
                              .distinct
                              .partitionBy(type2Axioms.partitioner.get)   
      val r2OutputCount = r2Output.count()
      t_end = System.nanoTime() 
      println("Delta1 + Delta2: " + r2OutputCount + 
          " Time taken: " + (t_end - t_begin)/1e6 + " ms")
      r2Output
    }
  }

  //completion rule 3
  def completionRule3(uAxioms: RDD[(Int, Int)], 
      type3Axioms: RDD[(Int, (Int, Int))]): RDD[(Int, (Int, Int))] = {
    val r3Join = type3Axioms.join(uAxioms)
    val r3Output = r3Join.map({ case (k, ((v1, v2), v3)) => (v1, (v3, v2)) })
                         .partitionBy(type3Axioms.partitioner.get)
                         .persist()
    r3Output

  }

  //completion rule 4
  def completionRule4(uAxioms: RDD[(Int, Int)], rAxioms: RDD[(Int, (Int, Int))], 
      type4Axioms: RDD[(Int, (Int, Int))]): RDD[(Int, Int)] = {

    println("Debugging with persist(StorageLevel.MEMORY_ONLY_SER)")
    
    var t_begin = System.nanoTime()
    val r4Join1 = type4Axioms.join(rAxioms)
    val r4Join1_count = r4Join1.persist(StorageLevel.MEMORY_ONLY_SER).count
    var t_end = System.nanoTime()
    println("type4Axioms.join(rAxioms). Count= " +r4Join1_count+", Time taken: "+(t_end - t_begin) / 1e6 + " ms")
    
    t_begin = System.nanoTime()
    val r4Join1ReMapped = r4Join1.map({ case (k, ((v1, v2), (v3, v4))) => (v1, (v2, (v3, v4))) })
    val r4Join1ReMapped_count = r4Join1ReMapped.persist(StorageLevel.MEMORY_ONLY_SER).count
    t_end = System.nanoTime()
    println("r4Join1.map(...). Count = " +r4Join1ReMapped_count+", Time taken: "+ (t_end - t_begin) / 1e6 + " ms")
    
    t_begin = System.nanoTime()
    val r4Join2 = r4Join1ReMapped.join(uAxioms)
    val r4Join2_count = r4Join2.persist(StorageLevel.MEMORY_ONLY_SER).count
    t_end = System.nanoTime()
    println("r4Join1ReMapped.join(uAxioms). Count= " + r4Join2_count+", Time taken: "+(t_end - t_begin) / 1e6 + " ms")
   
    t_begin = System.nanoTime()
    val r4Join2Filtered = r4Join2.filter({ case (k, ((v2, (v3, v4)), v5)) => v4 == v5 }).map({ case (k, ((v2, (v3, v4)), v5)) => (v2, v3) }).distinct
    val r4Join2Filtered_count = r4Join2Filtered.persist(StorageLevel.MEMORY_ONLY_SER).count
    t_end = System.nanoTime()
    println("r4Join2.filter().map(). Count = " +r4Join2Filtered_count +", Time taken: "+(t_end - t_begin) / 1e6 + " ms")
    
    r4Join2Filtered
  }
  
   def completionRule4_new(uAxioms: RDD[(Int, Int)], 
       rAxioms: RDD[(Int, (Int, Int))], 
       type4Axioms: RDD[(Int, (Int, Int))]): RDD[(Int, Int)] = {
    var t_begin = System.nanoTime()
    val r4Join1 = type4Axioms.join(rAxioms).map({ case (k, ((v1, v2), (v3, v4))) => (v4, (v2, (v3, v1))) })
    val r4Join1_count = r4Join1.persist(StorageLevel.MEMORY_ONLY_SER).count
    var t_end = System.nanoTime()
    println("type4Axioms.join(rAxioms). Count= " +r4Join1_count+", Time taken: "+(t_end - t_begin) / 1e6 + " ms")
    //debug
    println("r4Join1: #Partitions = " + r4Join1.partitions.size + 
        " Size = " + SizeEstimator.estimate(r4Join1) + 
        " Count = " + r4Join1_count + 
        ", Time taken: " + (t_end - t_begin) / 1e6 + " ms")
    
    if(r4Join1.isEmpty()) return sc.emptyRDD
    
    val uAxiomsFlipped = uAxioms.map({ case (k1, v5) => (v5, k1) })
    
    t_begin = System.nanoTime()
    val r4Join2 = r4Join1.join(uAxiomsFlipped)
    val r4Join2_count = r4Join2.persist(StorageLevel.MEMORY_ONLY_SER).count
    t_end = System.nanoTime()
    println("r4Join1ReMapped.join(uAxioms). Count= " + r4Join2_count+", Time taken: "+(t_end - t_begin) / 1e6 + " ms")
    //debug
    println("r4Join1: #Partitions = " + r4Join2.partitions.size + 
        " Size = " + SizeEstimator.estimate(r4Join2) + 
        " Count = " + r4Join2_count + 
        ", Time taken: " + (t_end - t_begin) / 1e6 + " ms")
    
    t_begin = System.nanoTime()
    val r4Join2Filtered = r4Join2.filter({ case (k, ((v2, (v3, v1)), k1)) => v1 == k1 }).map({ case (k, ((v2, (v3, v1)), k1)) => (v2, v3) }).distinct
    val r4Join2Filtered_count = r4Join2Filtered.persist(StorageLevel.MEMORY_ONLY_SER).count
    t_end = System.nanoTime()
    println("r4Join2.filter().map(). Count = " +r4Join2Filtered_count +", Time taken: "+(t_end - t_begin) / 1e6 + " ms")
    //debug
    println("r4Join2.filter().map(): #Partitions = " + r4Join2Filtered.partitions.size + 
        " Size = " + SizeEstimator.estimate(r4Join2Filtered) + 
        " Count = " + r4Join2Filtered_count + 
        ", Time taken: " + (t_end - t_begin) / 1e6 + " ms")
    
    r4Join2Filtered
  }
   
   def completionRule4CompoundKey(filteredUAxioms: RDD[(Int, Int)], 
       rAxioms: RDD[(Int, (Int, Int))], 
       type4Axioms: RDD[(Int, (Int, Int))]): RDD[(Int, Int)] = {
     println("-------------Rule4 using compound key-------------")
     var t_begin = System.nanoTime()
     val filteredUAxiomsYKey = filteredUAxioms.map({ case (a, y) => (y, a) })
     val rAxiomsPairYKey = rAxioms.map({ case (r, (x, y)) => (y, (r, x)) })
     val r4Join1 = filteredUAxiomsYKey.join(rAxiomsPairYKey)
                                      .map({ case (y, (a, (r, x))) => ((r, a), x) })
     val type4AxiomsPairKey = type4Axioms.map({ case (r, (a, b)) => ((r, a), b) })  
     val r4Join2 = type4AxiomsPairKey.join(r4Join1)
                                     .map({ case ((r, a), (b, x)) => (b, x) })
                                     .distinct
                                     .partitionBy(type4Axioms.partitioner.get)
     val r4Join2Count = r4Join2.count()
     var t_end = System.nanoTime()
     println("r4Join2Count: " + r4Join2Count + " Time taken: " + 
         (t_end - t_begin)/1e6 + " ms")
     r4Join2                                
   }
   
   def completionRule4_Raghava(filteredUAxioms: RDD[(Int, Int)], 
       rAxioms: RDD[(Int, (Int, Int))], 
       type4Axioms: RDD[(Int, (Int, Int))]): RDD[(Int, Int)] = { 
 //   var t_begin = System.nanoTime()
    val type4AxiomsFillerKey = type4Axioms.map({ case (r, (a, b)) => (a, (r, b)) })
                                          .partitionBy(type4Axioms.partitioner.get)
    val r4Join1 = type4AxiomsFillerKey.join(filteredUAxioms) 
//    val r4Join1Count = r4Join1.persist(StorageLevel.MEMORY_ONLY_SER).count()
//    var t_end = System.nanoTime()
        
//    t_begin = System.nanoTime()    
    val r4Join1YKey = r4Join1.map({ case (a, ((r1, b), y)) => (y, (r1, b)) })
    val rAxiomsPairYKey = rAxioms.map({ case (r2, (x, y)) => (y, (r2, x)) })
    val r4Join2 = r4Join1YKey.join(rAxiomsPairYKey)
//    val r4Join2Count = r4Join2.persist(StorageLevel.MEMORY_ONLY_SER).count()
//    t_end = System.nanoTime()
    
//    t_begin = System.nanoTime()
    val r4Result = r4Join2.filter({ case (y, ((r1, b), (r2, x))) => r1 == r2 })
                          .map({ case (y, ((r1, b), (r2, x))) => (b, x) })
                          .partitionBy(type4Axioms.partitioner.get)
                          .persist()
 //   val r4ResultCount = r4Result.count()
 //   var t_end = System.nanoTime()
   
     r4Result
   }

  //completion rule 5
  def completionRule5(rAxioms: RDD[(Int, (Int, Int))], 
      type5Axioms: RDD[(Int, Int)]): RDD[(Int, (Int, Int))] = {
    val r5Join = type5Axioms.join(rAxioms)
                            .map({ case (k, (v1, (v2, v3))) => (v1, (v2, v3)) })
                            .partitionBy(type5Axioms.partitioner.get)
                            .persist()
   // val rAxiomsNew = rAxioms.union(r5Join).distinct

    r5Join
  }

  //completion rule 6
  def completionRule6(rAxioms: RDD[(Int, (Int, Int))], type6Axioms: RDD[(Int, (Int, Int))]): RDD[(Int, (Int, Int))] = {

    var t_begin = System.nanoTime()
    val r6Join1 = type6Axioms.join(rAxioms).map({ case (k, ((v1, v2), (v3, v4))) => (v1, (v2, (v3, v4))) })
    val r6Join1_count = r6Join1.persist(StorageLevel.MEMORY_ONLY_SER).count
    var t_end = System.nanoTime()
    println("r6Join1=type6Axioms.join(rAxioms).map(). Count= " +r6Join1_count+", Time taken: "+(t_end - t_begin) / 1e6 + " ms")
    
    t_begin = System.nanoTime()
    val r6Join2 = r6Join1.join(rAxioms)
    val r6Join2_count = r6Join2.persist(StorageLevel.MEMORY_ONLY_SER).count
    t_end = System.nanoTime()
    println("r6Join2=r6Join1.join(rAxioms). Count= " +r6Join2_count+", Time taken: "+(t_end - t_begin) / 1e6 + " ms")
    
    t_begin = System.nanoTime()
    val r6Join2_filtered = r6Join2.filter({ case (k, ((v2, (v3, v4)), (v5, v6))) => v4 == v5 })
                                  .map({ case (k, ((v2, (v3, v4)), (v5, v6))) => (v2, (v3, v6)) })
                                  .distinct
                                  .partitionBy(type6Axioms.partitioner.get)
    val r6Join2_filtered_count = r6Join2_filtered.persist(StorageLevel.MEMORY_ONLY_SER).count
    t_end = System.nanoTime()
    println("r6Join2.filter().map(). Count= " +r6Join2_filtered_count+", Time taken: "+(t_end - t_begin) / 1e6 + " ms")
    
    // val rAxiomsNew = rAxioms.union(r6Join2).distinct

    r6Join2_filtered
  }

  //completion rule 6
  def completionRule6_new(rAxioms: RDD[(Int, (Int, Int))], type6Axioms: RDD[(Int, (Int, Int))]): RDD[(Int, (Int, Int))] = {

//    var t_begin = System.nanoTime()
    val r6Join1 = type6Axioms.join(rAxioms).map({ case (k, ((v1, v2), (v3, v4))) => (v4, (v1, v2, v3)) })
//    val r6Join1_count = r6Join1.persist(StorageLevel.MEMORY_ONLY_SER).count
//    var t_end = System.nanoTime()
//    println("r6Join1=type6Axioms.join(rAxioms).map(). Count= " +r6Join1_count+", Time taken: "+(t_end - t_begin) / 1e6 + " ms")
    
    if(r6Join1.isEmpty()) return sc.emptyRDD
    
    val rAxiomsReMapped = rAxioms.map({ case (r,(y,z)) => (y,(r,z))})
    
//    t_begin = System.nanoTime()
    val r6Join2 = r6Join1.join(rAxiomsReMapped)
//    val r6Join2_count = r6Join2.persist(StorageLevel.MEMORY_ONLY_SER).count
//    t_end = System.nanoTime()
//    println("r6Join2=r6Join1.join(rAxioms). Count= " +r6Join2_count+", Time taken: "+(t_end - t_begin) / 1e6 + " ms")
    
//    t_begin = System.nanoTime()
    val r6Join2_filtered = r6Join2.filter({ case (k, ((v1,v2,v3),(r,z))) => v1 == r })
                                  .map({ case (k, ((v1,v2,v3),(r,z))) => (v2, (v3, z)) })
                                  .distinct
                                  .partitionBy(type6Axioms.partitioner.get)
                                  .persist()
//    val r6Join2_filtered_count = r6Join2_filtered.persist(StorageLevel.MEMORY_ONLY_SER).count
//    var t_end = System.nanoTime()
//    println("r6Join2.filter().map(). Count= " + r6Join2_filtered_count + ", Time taken: "+(t_end - t_begin) / 1e6 + " ms")
    
    // val rAxiomsNew = rAxioms.union(r6Join2).distinct

    r6Join2_filtered
  }

  
  //Computes time of any function passed to it
  def time[R](block: => R): R = {
    val t0 = System.nanoTime()
    val result = block // call-by-name
    val t1 = System.nanoTime()
    println("Elapsed time: " + (t1 - t0) / 1e6 + " ms")
    result
  }
  
  def deleteDir(dirPath: String): Boolean = {
    val localFileScheme = "file:///"
    val hdfsFileScheme = "hdfs://"
    val fileSystemURI: URI = {
      if(dirPath.startsWith(localFileScheme))
        new URI(localFileScheme)
      else if(dirPath.startsWith(hdfsFileScheme))
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
  
  def saveAndReloadRDD[K: ClassTag, V: ClassTag](axiomsRDD: RDD[(K, V)], 
      dirPath: String, numPartitions: Int): RDD[(K, V)] = {
    println("DebugString len before save: " + axiomsRDD.toDebugString.length())
    axiomsRDD.saveAsObjectFile(dirPath)
    val reloadedRDD = sc.objectFile[(K, V)](dirPath, numPartitions)
                        .partitionBy(hashPartitioner)
                        .persist()
    // run an action to force reading rdd from disk
    reloadedRDD.count()                    
    deleteDir(dirPath)
    println("DebugString len after reload: " + reloadedRDD.toDebugString.length()) 
    reloadedRDD
  }

  /*
   * The main method that initializes and calls each function corresponding to the completion rule 
   */
  def main(args: Array[String]): Unit = {
    if (args.length != 3) {
      System.err.println("Missing args:\n\t 1. path of directory containing " + 
              "the axiom files \n\t 2. output directory to save the computed " + 
              "sAxioms \n\t 3. Number of worker nodes in the cluster ")
      System.exit(-1)
    }

    //init time
    val t_init = System.nanoTime()

//    sc.setCheckpointDir(args(3))
//    kryo.register(ClassTag(Class.forName("org.apache.spark.util.collection.CompactBuffer")).wrap.runtimeClass)
    conf.registerKryoClasses(Array(Class.forName("[Lorg.apache.spark.util.collection.CompactBuffer;")))
    deleteDir(args(1))
    
    val numProcessors = Runtime.getRuntime.availableProcessors()
    val numPartitions = numProcessors * args(2).toInt
    hashPartitioner = new HashPartitioner(numPartitions)

    var (uAxioms, rAxioms, type1Axioms, type2Axioms, type3Axioms, type4Axioms, 
        type5Axioms, type6Axioms) = initializeRDD(sc, args(0))
//    uAxioms = uAxioms.persist(StorageLevel.MEMORY_AND_DISK)
   
   
    println("Before closure computation. Initial uAxioms count: " + 
        uAxioms.count + ", Initial rAxioms count: " + rAxioms.count)
    
    // used for filtering of axioms in rule2
    val type2Conjuncts = type2Axioms.collect().map({ case (a1,(a2,b)) => (a1,a2)}).toSet
    val type2ConjunctsBroadcast = {
      if (!type2Conjuncts.isEmpty)
        sc.broadcast(type2Conjuncts)
      else
        null
      }    
    // used for filtering of uaxioms in rule4
    val type4Fillers = type4Axioms.collect().map({ case (k, (v1, v2)) => v1 }).toSet
    val type4FillersBroadcast = { 
      if (!type4Fillers.isEmpty)
        sc.broadcast(type4Fillers) 
      else 
        null
      }
    
    var currUAllRules = uAxioms
    var currRAllRules = rAxioms
    
    var currUAllRulesCount = uAxioms.count
    var currRAllRulesCount = rAxioms.count
    
    var prevUAllRulesCount: Long = 0
    var prevRAllRulesCount: Long = 0 
//    var prevUAllRules: RDD[(Int, Int)] = null
//    var prevRAllRules: RDD[(Int, (Int, Int))] = null
    var prevDeltaURule1: RDD[(Int, Int)] = null
    var currDeltaURule1: RDD[(Int, Int)] = null
    var prevDeltaURule2: RDD[(Int, Int)] = null 
    var currDeltaURule2: RDD[(Int, Int)] = null
    var prevDeltaRRule3: RDD[(Int, (Int, Int))] = null 
    var currDeltaRRule3: RDD[(Int, (Int, Int))] = null
    var prevDeltaURule4: RDD[(Int, Int)] = null 
    var currDeltaURule4: RDD[(Int, Int)] = null
    var prevDeltaRRule5: RDD[(Int, (Int, Int))] = null 
    var currDeltaRRule5: RDD[(Int, (Int, Int))] = null
    var prevDeltaRRule6: RDD[(Int, (Int, Int))] = null 
    var currDeltaRRule6: RDD[(Int, (Int, Int))] = null
     
     var counter = 1
     
     while (prevUAllRulesCount != currUAllRulesCount || prevRAllRulesCount != currRAllRulesCount) {

       var t_beginLoop = System.nanoTime()

       var t_beginRule = System.nanoTime()
       var t_endRule:Long = 0
       var inputURule1: RDD[(Int, Int)] = { 
         if (counter == 1)
           currUAllRules
         else  
           sc.union(prevDeltaURule1, prevDeltaURule2, prevDeltaURule4) 
             .distinct
             .partitionBy(type1Axioms.partitioner.get)
             .persist()
         }
//       inputURule1 = saveAndReloadRDD(inputURule1, args(1), numPartitions)
//       val inputURule1Count = inputURule1.persist().count()
//       if (inputURule1Count != 0) {
       currDeltaURule1 = completionRule1(inputURule1, type1Axioms) //Rule1
       currDeltaURule1 = saveAndReloadRDD(currDeltaURule1, args(1), numPartitions)
       val currDeltaRule1Count = currDeltaURule1.count()
       t_endRule = System.nanoTime()
       println("----Completed rule1----")
       println("count: " + currDeltaRule1Count + " Time taken: " + 
           (t_endRule - t_beginRule) / 1e6 + " ms")
       println("=====================================")
//       }
//       else
//         println("----Skipping rule1----")
       
       t_beginRule = System.nanoTime()  
       currUAllRules = sc.union(currUAllRules, currDeltaURule1)
                         .distinct.partitionBy(type2Axioms.partitioner.get)
                         .persist()
                         
       val inputURule2 = { 
         if (counter == 1)
           currUAllRules 
         else
           sc.union(prevDeltaURule2, prevDeltaURule4, currDeltaURule1)
             .distinct.partitionBy(type2Axioms.partitioner.get)
         }  
       currDeltaURule2 = { 
         if (type2ConjunctsBroadcast != null)
           completionRule2_deltaNew(type2ConjunctsBroadcast.value, 
               inputURule2, currUAllRules, type2Axioms)  
         else
           sc.emptyRDD[(Int, Int)]
         }
       currDeltaURule2 = saveAndReloadRDD(currDeltaURule2, args(1), numPartitions)
       val currDeltaRule2Count = currDeltaURule2.count()
       t_endRule = System.nanoTime()
       println("----Completed rule2----")
       println("count: " + currDeltaRule2Count + " Time taken: " + 
           (t_endRule - t_beginRule) / 1e6 + " ms")
       println("=====================================")
       
       t_beginRule = System.nanoTime()
       val inputURule3 = { 
         if (counter == 1)
           sc.union(currUAllRules, currDeltaURule1, currDeltaURule2)
             .distinct
             .partitionBy(type3Axioms.partitioner.get)
             .persist()
         else
           sc.union(prevDeltaURule4, currDeltaURule1, currDeltaURule2) 
             .distinct
             .partitionBy(type3Axioms.partitioner.get)
             .persist()
         }
//       val inputURule3Count = inputURule3.persist().count()
//       if (inputURule3Count != 0) {
       currDeltaRRule3 = completionRule3(inputURule3, type3Axioms) //Rule3
       currDeltaRRule3 = saveAndReloadRDD(currDeltaRRule3, args(1), numPartitions)
       val currDeltaRRule3Count = currDeltaRRule3.count()
       t_endRule = System.nanoTime()
       println("----Completed rule3----")
       println("count: " + currDeltaRRule3Count + " Time taken: " + 
           (t_endRule - t_beginRule) / 1e6 + " ms")
       println("=====================================")
//       }
//       else 
//         println("----Skipping rule3----")
       
       t_beginRule = System.nanoTime()  
       // caching this RDD since its used in rule6
       val inputRRule4 = sc.union(currRAllRules, currDeltaRRule3)
                           .distinct
                           .partitionBy(type4Axioms.partitioner.get)
                           .persist()
      val inputURule4 = sc.union(currUAllRules, currDeltaURule1, currDeltaURule2)
                          .distinct
                          .partitionBy(type4Axioms.partitioner.get)
                          .persist()
      
      val filteredUAxiomsRule2 = { 
        if (type4FillersBroadcast != null)
          inputURule4.filter({ 
              case (k, v) => type4FillersBroadcast.value.contains(k) })
                     .partitionBy(type4Axioms.partitioner.get) 
        else
          sc.emptyRDD[(Int, Int)]
        }  
      currDeltaURule4 = completionRule4_Raghava(filteredUAxiomsRule2, 
          inputRRule4, type4Axioms)
      currDeltaURule4 = saveAndReloadRDD(currDeltaURule4, args(1), numPartitions)
      val currDeltaURule4Count = currDeltaURule4.count()
      t_endRule = System.nanoTime()
      println("----Completed rule4----")
      println("count: " + currDeltaURule4Count + " Time taken: " + 
          (t_endRule - t_beginRule) / 1e6 + " ms")
      println("=====================================")

      t_beginRule = System.nanoTime()
      val inputRRule5 = { 
         if (counter == 1)
           inputRRule4
         else
           sc.union(prevDeltaRRule5, prevDeltaRRule6, currDeltaRRule3)
             .distinct
             .partitionBy(type4Axioms.partitioner.get)
             .persist()
         }
//      val inputRRule5Count = inputRRule5.persist().count()
//      if (inputRRule5Count != 0) {
        currDeltaRRule5 = completionRule5(inputRRule5, type5Axioms) //Rule5  
        currDeltaRRule5 = saveAndReloadRDD(currDeltaRRule5, args(1), numPartitions)
        val currDeltaRRule5Count = currDeltaRRule5.count()
        t_endRule = System.nanoTime()
        println("----Completed rule5----")
        println("count: " + currDeltaRRule5Count + " Time taken: " + 
            (t_endRule - t_beginRule) / 1e6 + " ms")
        println("=====================================")
//      }
//      else
//        println("----Skipping rule5----")
      
      t_beginRule = System.nanoTime()  
      val inputRRule6 = sc.union(inputRRule4, currDeltaRRule5)
                          .distinct
                          .partitionBy(type6Axioms.partitioner.get)
                          .persist()
//      val inputRRule6Count = inputRRule6.persist().count()  
//      if (inputRRule6Count != 0) {
        currDeltaRRule6 = completionRule6_new(inputRRule6, type6Axioms) //Rule6  
        currDeltaRRule6 = saveAndReloadRDD(currDeltaRRule6, args(1), numPartitions)
        val currDeltaRRule6Count = currDeltaRRule6.count()
        t_endRule = System.nanoTime()
        println("----Completed rule6----")
        println("count: " + currDeltaRRule6Count + " Time taken: " + 
            (t_endRule - t_beginRule) / 1e6 + " ms")
        println("=====================================")
//      }
//      else
//        println("----Skipping rule6----")

      prevUAllRulesCount = currUAllRulesCount
      prevRAllRulesCount = currRAllRulesCount
      
      //store the union of all new axioms 
      var t_begin_uAxiomCount = System.nanoTime() 
      currUAllRules = sc.union(currUAllRules, currDeltaURule1, currDeltaURule2, 
          currDeltaURule4)
                        .distinct
                        .partitionBy(hashPartitioner)
                        .persist()

      var t_saveBegin = System.nanoTime()
      currUAllRules = saveAndReloadRDD(currUAllRules, args(1), numPartitions)
      var t_saveEnd = System.nanoTime()
      println("currUAllRules saved to disk in loop " + counter + 
          " Time taken: "  + (t_saveEnd-t_saveBegin)/1e6 + " ms")  

      currUAllRulesCount = currUAllRules.count()
      var t_end_uAxiomCount = System.nanoTime()
      println("Time taken for uAxiom count: " + 
          (t_end_uAxiomCount - t_begin_uAxiomCount) / 1e6 + " ms")
      println("------Completed uAxioms count--------")
        
      var t_begin_rAxiomCount = System.nanoTime()
      currRAllRules = sc.union(currRAllRules, currDeltaRRule3, currDeltaRRule5, 
          currDeltaRRule6)
                        .distinct
                        .partitionBy(hashPartitioner)
                        .persist()
      
      t_saveBegin = System.nanoTime()
      currRAllRules = saveAndReloadRDD(currRAllRules, args(1), numPartitions)
      t_saveEnd = System.nanoTime()
      println("currRAllRules saved to disk in loop " + counter + 
          " Time taken: "  + (t_saveEnd-t_saveBegin)/1e6 + " ms")      

      currRAllRulesCount = currRAllRules.count() 
      var t_end_rAxiomCount = System.nanoTime()
      println("Time taken for rAxiom count: " + 
          (t_end_rAxiomCount - t_begin_rAxiomCount) / 1e6 + " ms")
      println("------Completed rAxioms count--------")
       
      //time
      var t_endLoop = System.nanoTime()
      
      //debugging
      println("===================================debug info=========================================")
      println("End of loop: " + counter + ". New uAxioms count: " + 
          currUAllRulesCount + ", New rAxioms count: " + currRAllRulesCount)
      println("Runtime of the current loop: " + (t_endLoop - t_beginLoop) / 1e6 + " ms\n")
      println("======================================================================================")    
      
      prevDeltaURule1 = currDeltaURule1
      prevDeltaURule2 = currDeltaURule2
      prevDeltaRRule3 = currDeltaRRule3
      prevDeltaURule4 = currDeltaURule4
      prevDeltaRRule5 = currDeltaRRule5
      prevDeltaRRule6 = currDeltaRRule6
      
      //unpersist the cached rdds      
      currDeltaURule1.unpersist()
      currDeltaURule2.unpersist()
      currDeltaRRule3.unpersist()
      currDeltaURule4.unpersist()
      currDeltaRRule5.unpersist()
      currDeltaRRule6.unpersist()
      currUAllRules.unpersist()
      currRAllRules.unpersist()
      
      //loop counter 
      counter = counter + 1

    } //end of loop

    println("Closure computed. Final number of uAxioms: " + currUAllRulesCount)
    
    val t_end = System.nanoTime()

    //collect result (new subclass axioms) into 1 partition and spit out the result to a file.
    val sAxioms = currUAllRules.map({ case (v1, v2) => v2 + "|" + v1 }) // invert uAxioms to sAxioms
    sAxioms.coalesce(1, true).saveAsTextFile(args(1)) // coalesce to 1 partition so output can be written to 1 file
    println("Total runtime of the program: " + (t_end - t_init) / 1e6 + " ms")
    sc.stop()
  }
  
}