/*
 * Implement the paper: 
 * "Recency-Based Collaborative Filtering", Yi Ding, Xue Li, and Maria E. Orlowska, 2006
 *
 * Implement two algorithms from the paper: 
 * 1. Recency-Based Collaborative Filtering Recommedation
 * 2. Item-based collaborative filtering Recommedation
 *
 */

// 注意 userID movieID 與 userIndex movieIndex 分別差 1

import scala.io.Source
import scala.util.Random
import scala.math
import java.nio.charset.CodingErrorAction
import scala.io.Codec

object Recommendation {


		//resolve the problem of the encoding in movie file
		implicit val codec = Codec("UTF-8")
		codec.onMalformedInput(CodingErrorAction.REPLACE)
		codec.onUnmappableCharacter(CodingErrorAction.REPLACE)		

		case class MovieDataStructure(movieID: Int, title: String, genres: String)
		case class UserDataStructure(userID: Int, gender: Char, age: Int, occupation: Int, zipCode: String)
		case class RatingDataStructure(userID: Int, movieID: Int, rating: Double, timestamp: Long)

		//val ratingFile = Source.fromFile("../dataset/ml-1m/ratings.dat")
		val ratingFile = Source.fromFile("u.data")
			.getLines
			.toList
			.map{line =>
				//val fields = line.split("::")
				val fields = line.split("\t")
				val tempRating = RatingDataStructure(fields(0).toInt, fields(1).toInt, fields(2).toDouble, fields(3).toLong)
				tempRating
				}
	
		val similarityThreshold = 30 
		val alpha = 0.7
		val ratingScale = 5.0
		val trainingUserSize = 400
		val numUsers = ratingFile 
		                .reduceLeft( (a,b) => if (a.userID > b.userID) a else b) 
		                .userID
		val numMovies = ratingFile 
		                 .reduceLeft( (a,b) => if (a.movieID > b.movieID) a else b) 
		                 .movieID
		//println(numMovies+ " " + numUsers)

		//隨機從numUsers個users中挑出trainingUserSize個traning users
		val userCandidate = List.range(1, numUsers+1)
		def generateTestUsers(candidate: List[Int],count: Int, n: Int): List[Int] = {
			if(count == 0)
				Nil
			else{
				val i = Random.nextInt(n)	
				candidate(i) :: generateTestUsers((candidate.take(i) ::: candidate.drop(i+1)), count-1, n-1)
			}
		}
		val trainUsers = generateTestUsers(userCandidate, trainingUserSize, numUsers)
		//println(trainUsers)

		//將training users的評分資料存入 global variable "ratings" 中
		//val ratings = (for(user <- 1 to trainingUserSize) yield {
		val ratings = (for(user <- trainUsers) yield {
							val ratingArray = Array.fill[Double](numMovies)(0.0)
							val oneUserRatings = ratingFile.filter( _.userID == user )
							oneUserRatings.foreach( x => ratingArray(x.movieID - 1) = x.rating)
							ratingArray.toVector
						}).toVector
	
/*
	//my test data
	val ratings: Vector[Vector[Double]] = Vector(
				  Vector(1,4,2,0,4),
		          Vector(4,2,5,2,0),
		          Vector(4,3,0,3,0),
		          Vector(2,4,3,0,2)
		          )
	
	//原paper上的測試 example
	val ratings: Vector[Vector[Double]] = Vector(
				  Vector(1,4,2,5,4),
		          Vector(4,2,5,2,0),
		          Vector(4,3,0,3,3)	          
		          )

	val testUser: Vector[Double] = Vector(2,4,3,0,2) 
          
	val similarityThreshold = 2
	val alpha = 1.0
	val ratingScale = 5.0
	val numUsers = ratings.size
	val numMovies = ratings(0).size
	val targetMovieIndex = 3
	val trainingUserSize = numUsers
	val testTime: Vector[Long] = Vector(1095811200,1103846400,1035244800,0,1058832000)
*/

	//儲存similarity的資料結構，可查詢兩兩電影的相似度
	case class Similarity(similarValue: (Int, Int) => Double) {

		private val table = 
			(for(i <- 0 until numMovies) yield {
				//similarity資料存成Triangular matrix
				(for (j <- 0 until numMovies) yield {
					if(i > j)
						-1.0
					else{
						similarValue(i,j)	
					}
				}).toVector
			}).toVector

		//存取 Triangular matrix
		def sim(movie1: Int, movie2: Int) = {
			val i = movie1.min(movie2)
			val j = movie1.max(movie2)	
			table(i)(j)
		}
	}

	def coratedMovies(movie1: Int, movie2: Int) = {

		def iterateMovies(userIndex: Int): List[(Double, Double, Int)] = {
			if(userIndex == trainingUserSize)
				Nil
			else{
				if((ratings(userIndex)(movie1) > 0.0) && (ratings(userIndex)(movie2) > 0.0)){
					( ratings(userIndex)(movie1) , ratings(userIndex)(movie2) , userIndex) :: iterateMovies(userIndex + 1)
				}else{
					iterateMovies(userIndex + 1)	
				}
			}
		}
		iterateMovies(0)
	}

	def cosineSimilarity(movie1: Int, movie2: Int) = {
		
		val zipRatings = coratedMovies(movie1, movie2)
		//println(zipRatings)

		//!! how to process empty
		if(zipRatings.isEmpty)
			0.0
		else {
			//以下對應到原 paper 的 equation(1)
			val value = zipRatings 
			             .map{ case(m1, m2, user) => (m1 * m2 , m1 * m1 , m2 * m2) } 
			             .reduceLeft((a,b) => (a._1 + b._1 , a._2 + b._2 , a._3 + b._3 ))

			val denominator = ( math.sqrt(value._2) * math.sqrt(value._3) )
			
			//it won't happen unless zero vector(i.e. a movie withou any rating)
			if(denominator == 0.0)
				println("  !!!!!! cosineSimilarity denominator==0")

			value._1 / denominator
		}	
	}

	def pearsonSimilarity(movie1: Int, movie2: Int) = {


		val zipRatings = coratedMovies(movie1, movie2)
		//println(zipRatings)

		//!! how to process empty
		if(zipRatings.isEmpty)
			0.0
		else {
			//以下對應到原 paper 的 equation(2)
			val value = zipRatings 
			             .map{ case(m1, m2, user) => 
			             	     val average = ratings(user).reduceLeft(_+_) / ratings(user).count(_ != 0.0) 
			             	     val v1 = m1 - average - 0.0001 
			             	     val v2 = m2 - average - 0.0001
			             	     (v1 * v2 , v1 * v1 , v2 * v2) 
			             	 } 
			             .reduceLeft((a,b) => (a._1 + b._1 , a._2 + b._2 , a._3 + b._3 ))
			//println(value._1 + " " + value._2 + " " + value._3)

			val denominator = ( math.sqrt(value._2) * math.sqrt(value._3) )

			//!! Almost because there is only one zipRating element i.e. only one co-rating
			//resolve this problem by subtracting a small number 0.0001
			if(denominator == 0.0){
				println(" !!! pearsonSimilarity denominator==0 , zipRatings: " + zipRatings)
			}

			val similarValue = value._1 / denominator

			//linear map
			(similarValue + 1.0) / 2.0
		}
	}

	//
	//回傳closure function，綁定normalizeTable，
	//使得在case class Similarity中計算 similarity時，同時可查詢user評分的normalize值
	def recencySimilarityClosure() = {

		def gaussianNormalize(matrix: Vector[Vector[Double]]): Vector[Vector[Double]] = {
			//println("Gaussian normalization table was Constructed.")
			matrix.map{ row =>
				//以下對應到原 paper 的 equation(7)
				val nonzeroElements = row.filter(_ != 0.0)
				val numNonzero = nonzeroElements.size
				val average = row.reduceLeft(_+_) / numNonzero

				val denomi1 = 1.0/(numNonzero-1.0)
				val denomi2 = nonzeroElements
				               .map{ x => (x-average)*(x-average)}
				               .reduceLeft(_+_)
				val denominator = math.sqrt(denomi1 * denomi2)

				row.map{ x => x match {
						case 0.0 => 0
						case _ => 
							if(denominator == 0.0)
								println("  !!!!!! normalize denominator==0")							
							( x - average) / denominator
					}}
			}
		}

		val normalizeTable = gaussianNormalize(ratings)

		val closureFunction = (movie1: Int, movie2: Int) => {
			
				//以下對應到原 paper 的 equation(8)
				var count = 0
				var distance = 0.0

				for(user <- 0 until trainingUserSize){
					if((ratings(user)(movie1) > 0.0) && (ratings(user)(movie2) > 0.0)){
						count = count + 1
						distance = distance + math.abs(normalizeTable(user)(movie1)-normalizeTable(user)(movie2))
					}
				}

				//!! there is no any co-rating of both movies,
				// we do not know their similarity
				if(count == 0.0) {
					//println("  !!!!!! recencySimilarity denominator==0")
					0.0
					//Random.nextDouble()
				}else {
					val averageManhattanDistance = distance / count
					//原 paper 的 equation(6)
					math.exp(-averageManhattanDistance)
				}
		}

		closureFunction
	}

	//對每部電影儲存的nearest neighbors(measure by similarity)，排序、有threshold
	//Return type : Vector[List[Int]]
	//def nearestNeighbors(matrix: Vector[Vector[Double]], threshold: Double): Vector[List[Int]] ={
	def nearestNeighbors(similarTable: Similarity, targetUserVector: Vector[Double]) = {

		def findNearest(targetMovie: Int): List[Int] = {
			//println(targetMovie)
			def iterateEachMovie(movieIndex: Int): List[Int] = {
				//println(movieIndex +" " + numMovies)

				movieIndex match {
					case index if (index == numMovies) => Nil
					case index if (index != targetMovie) =>
						//print(similarTable.sim(index, targetMovie) + " ")
						if(targetUserVector(index) > 0.0)
							index :: iterateEachMovie(movieIndex+1)
						else
							iterateEachMovie(movieIndex+1)
					case _ => iterateEachMovie(movieIndex+1)

				}

			}

			//!! notice sortWith comparing by NaN
			//!! SORTING IS THE PART OF ALGORITHM
			iterateEachMovie(0)
				.sortWith( (a,b) => similarTable.sim(a,targetMovie) > similarTable.sim(b,targetMovie) )
				.take(similarityThreshold)
		
		} //end of def findNearest()

		(for(movie <- 0 until numMovies) yield {
			findNearest(movie)
		}).toVector
		
	} //end of def nearestNeighbors()

	//Tradition item-based CF
	def itemBasedPredict(similarityTable: Similarity, 
		                 targetUserVector: Vector[Double], 
		                 testTime: Vector[Long], 
		                 targetMovieIndex: Int ) = {

		var nearestTable = nearestNeighbors(similarityTable, targetUserVector)

		if(nearestTable(targetMovieIndex).isEmpty)
			-1.0
		else{
			//以下對應到原 paper 的 equation(3)

			val value1 = nearestTable(targetMovieIndex).map{ x =>
							targetUserVector(x) * similarityTable.sim(targetMovieIndex, x) 
						}.reduceLeft(_+_)
			val value2 = nearestTable(targetMovieIndex).map{ x =>
							math.abs(similarityTable.sim(targetMovieIndex, x))
						}.reduceLeft(_+_)

			//!! 沒人評價過targetMovie時，targetMovie與其他電影similarity皆為零，
			//    prediction分母便為零。resolved
			if(value2 == 0){
				println("  !!!!!! cosineSimilarity denominator==0")
			}

			value1 / value2
			
		}

	}

	//Recency-based CF
	def recencyBasedPredict(similarityTable: Similarity, 
		                    targetUserVector: Vector[Double], 
		                    testTime: Vector[Long], 
		                    targetMovieIndex: Int ) = {

		var nearestTable = nearestNeighbors(similarityTable, targetUserVector)

		def ratingWeight(): List[Double] = {
			def recentNearestNeighbor(): Int = {
					nearestTable(targetMovieIndex)
					.map(x => (x, testTime(x)))
					.reduceLeft( (a,b) => if(a._2 < b._2) b else a)
					._1
			}

			//Train時 唯一用到時間因素的地方
			val recentMovie = recentNearestNeighbor()
			//println("  recent movie: " + k)

			//以下對應到原 paper 的 equation(10)
			nearestTable(targetMovieIndex).map{ i =>		
				val recentRating = targetUserVector(recentMovie)
				math.pow(1.0 - math.abs(targetUserVector(i)-recentRating)/ratingScale, alpha)	
			}


		}

		//!! what if empty
		if(nearestTable(targetMovieIndex).isEmpty)
			-1.0
		else{
			//以下對應到原 paper 的 equation(9)
			
			val weightList = ratingWeight()
			
			val nearestZipWeight = nearestTable(targetMovieIndex).zip(weightList)
			val value1 = nearestZipWeight.map{ x =>
							targetUserVector(x._1) * similarityTable.sim(targetMovieIndex, x._1) * x._2
						}.reduceLeft(_+_)
			val value2 = nearestZipWeight.map{ x =>
							similarityTable.sim(targetMovieIndex, x._1) * x._2
						}.reduceLeft(_+_)
	
			//!! 沒人評價過targetMovie時，targetMovie與其他電影similarity皆為零，
			//    prediction分母便為零。resolved
			if(value2 == 0){
				println("  !!!!!! cosineSimilarity denominator==0")
			}	

			value1 / value2
			
		}

	}

	def main(args: Array[String]){

		//!!move the follow code to top

		//選擇不同的Experiment configuration
		val selection = 5 

		//Train 
		//Training data is "ratings: Vector" 
		val (predictFunction, similarityTable) = selection match{
			case 1 => (recencyBasedPredict _, Similarity(cosineSimilarity))
			case 2 => (recencyBasedPredict _, Similarity(pearsonSimilarity)) 
			case 3 => (recencyBasedPredict _, Similarity(recencySimilarityClosure()))
			case 4 => (itemBasedPredict _, Similarity(pearsonSimilarity))
			case 5 => (itemBasedPredict _, Similarity(cosineSimilarity))

			//case _ => println("Not correct selection")
		}

		//Predict

		var mae: Double = 0.0
		var maeCount: Int = 0

		//從所有 users 中過濾掉 training users，剩下的就就是 test users
		val testUsers = userCandidate.filterNot(trainUsers.toSet)
		//println(testUsers)

		//for(user <- (trainingUserSize + 1) to numUsers ){
		for(user <- testUsers){
			//println(user + "th user test")
			val ratingArray = Array.fill[Double](numMovies)(0.0)
			val timeArray = Array.fill[Long](numMovies)(0)
			val oneUserRatings = ratingFile.filter( _.userID == user )
			
			oneUserRatings.foreach{ x => 
				ratingArray(x.movieID - 1) = x.rating
				timeArray(x.movieID - 1) = x.timestamp
			}
			val testUser = ratingArray.toVector
			val testTime = timeArray.toVector
			
			//原 paper 的 Experiment : 
			// "In All But One, the newest rated items for each user are used for testing."
			val targetMovieIndex = oneUserRatings 
			                        .reduceLeft( (a,b) => if (a.timestamp > b.timestamp) a else b) 
			                        .movieID - 1

			// 檢查 targetMovie 是否有 user (in training data) 評分過
			var movieBeRated = false
			for(i <- 0 until trainingUserSize)
				if(ratings(i)(targetMovieIndex) > 0.0)
					movieBeRated = true

			if(movieBeRated){
				// 當 targetMovie 有其他評分時

				// 開始測試
				val predictValue = predictFunction(similarityTable, testUser, testTime, targetMovieIndex)

				if(! predictValue.isNaN) {
					mae = mae + math.abs(predictValue - testUser(targetMovieIndex))
					maeCount = maeCount + 1
				}

				println("User " + user + " and movie " + targetMovieIndex + " : ")
				println(" Predic rating " + "%.3f".format(predictValue) )
				println(" Actual rating " + testUser(targetMovieIndex))
				println					
			}else{
				print("No user rate the movie " + targetMovieIndex )
				println(" ,we skip this test data")
				println
			}

		} //end of for(user <- )

		//原 paper 的 equation(11)
		println("MAE = " + "%.3f".format(mae / maeCount) )


	} //end of def main

} //end of object Recommendation

