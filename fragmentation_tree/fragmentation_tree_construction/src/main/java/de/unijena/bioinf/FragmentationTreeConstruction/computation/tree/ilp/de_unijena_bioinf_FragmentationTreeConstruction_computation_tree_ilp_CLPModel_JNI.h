/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_JNI */

#ifndef _Included_de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_JNI
#define _Included_de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_JNI
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_JNI
 * Method:    n_ctor
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_1JNI_n_1ctor
  (JNIEnv *, jobject, jint, jint);

/*
 * Class:     de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_JNI
 * Method:    n_dispose
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_1JNI_n_1dispose
  (JNIEnv *, jobject, jint);

/*
 * Class:     de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_JNI
 * Method:    n_getInfinity
 * Signature: (I)D
 */
JNIEXPORT jdouble JNICALL Java_de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_1JNI_n_1getInfinity
  (JNIEnv *, jobject, jint);

/*
 * Class:     de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_JNI
 * Method:    n_setObjective
 * Signature: (I[D)V
 */
JNIEXPORT void JNICALL Java_de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_1JNI_n_1setObjective
  (JNIEnv *, jobject, jint, jdoubleArray);

/*
 * Class:     de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_JNI
 * Method:    n_setTimeLimit
 * Signature: (ID)V
 */
JNIEXPORT void JNICALL Java_de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_1JNI_n_1setTimeLimit
  (JNIEnv *, jobject, jint, jdouble);

/*
 * Class:     de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_JNI
 * Method:    n_setColBounds
 * Signature: (I[D[D)V
 */
JNIEXPORT void JNICALL Java_de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_1JNI_n_1setColBounds
  (JNIEnv *, jobject, jint, jdoubleArray, jdoubleArray);

/*
 * Class:     de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_JNI
 * Method:    n_setColStart
 * Signature: (I[D)V
 */
JNIEXPORT void JNICALL Java_de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_1JNI_n_1setColStart
  (JNIEnv *, jobject, jint, jdoubleArray);

/*
 * Class:     de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_JNI
 * Method:    n_addFullRow
 * Signature: (I[DDD)V
 */
JNIEXPORT void JNICALL Java_de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_1JNI_n_1addFullRow
  (JNIEnv *, jobject, jint, jdoubleArray, jdouble, jdouble);

/*
 * Class:     de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_JNI
 * Method:    n_addSparseRow
 * Signature: (I[D[IDD)V
 */
JNIEXPORT void JNICALL Java_de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_1JNI_n_1addSparseRow
  (JNIEnv *, jobject, jint, jdoubleArray, jintArray, jdouble, jdouble);

/*
 * Class:     de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_JNI
 * Method:    n_addSparseRowCached
 * Signature: (I[D[IDD)V
 */
JNIEXPORT void JNICALL Java_de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_1JNI_n_1addSparseRowCached
  (JNIEnv *, jobject, jint, jdoubleArray, jintArray, jdouble, jdouble);

/*
 * Class:     de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_JNI
 * Method:    n_addSparseRows
 * Signature: (II[I[D[I[D[D)V
 */
JNIEXPORT void JNICALL Java_de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_1JNI_n_1addSparseRows
  (JNIEnv *, jobject, jint, jint, jintArray, jdoubleArray, jintArray, jdoubleArray, jdoubleArray);

/*
 * Class:     de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_JNI
 * Method:    n_solve
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_1JNI_n_1solve
  (JNIEnv *, jobject, jint);

/*
 * Class:     de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_JNI
 * Method:    n_getColSolution
 * Signature: (I)[D
 */
JNIEXPORT jdoubleArray JNICALL Java_de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_1JNI_n_1getColSolution
  (JNIEnv *, jobject, jint);

/*
 * Class:     de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_JNI
 * Method:    n_getScore
 * Signature: (I)D
 */
JNIEXPORT jdouble JNICALL Java_de_unijena_bioinf_FragmentationTreeConstruction_computation_tree_ilp_CLPModel_1JNI_n_1getScore
  (JNIEnv *, jobject, jint);

#ifdef __cplusplus
}
#endif
#endif