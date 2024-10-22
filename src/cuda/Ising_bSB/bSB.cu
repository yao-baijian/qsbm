#include <cuda_runtime_api.h> // cudaMalloc, cudaMemcpy, etc.
#include <cuda.h>
#include <cusparse.h>         // cusparseSpMV
#include <stdio.h>            // printf
#include <stdlib.h>           // EXIT_FAILURE
#include <fstream>
#include <iostream>
#include <random>
#include <chrono>
#include <string>
#include "GEAM.h"
#define BLOCK_SIZE 512
// Kernel for reducing a vector
__global__ void reduce_sum_float(float *d_out, float *d_in, unsigned int size) {
    // Calculate thread ID
    unsigned int idx = threadIdx.x + blockDim.x * blockIdx.x;
    unsigned int tid = threadIdx.x;
    __shared__ float sdata[BLOCK_SIZE];
    sdata[tid] = (idx < size) ? d_in[idx] : 0;
    // Boundary check
    if (idx >= size) {
        return;
    }
    // printf("idx: %d d_in: %f\n", idx, d_in[idx]);
    // Shared memory for this block

    // printf("size: %d tid: %d idx: %d sdata[8]: %f\n", size, tid, idx, sdata[8]);

    __syncthreads(); // Make sure the entire block is loaded!

    // Do reduction in shared mem
    for (unsigned int s = blockDim.x / 2; s > 0; s >>= 1) {
        if (tid < s) {
            // printf("s: %d tid: %d sdata[tid]: %f sdata[tid + s]: %f\n", s, tid, sdata[tid], sdata[tid + s]);
            // printf("sdata[8]: %f\n", sdata[8]);
            sdata[tid] += sdata[tid + s];
        }
        __syncthreads(); // Make sure all additions at one stage are done!
    }

    // Only thread 0 writes result for this block back to global mem
    if (tid == 0) {
        // printf("sdata[0]: %f\n", sdata[0]);
        d_out[blockIdx.x] = sdata[0];
    }
}

__global__ void reduce_sum_float_res(float *d_out, float *d_in, unsigned int size, float* res, float* mc, float total_weights) {
    // Calculate thread ID
    unsigned int idx = threadIdx.x + blockDim.x * blockIdx.x;
    unsigned int tid = threadIdx.x;
    __shared__ float sdata[BLOCK_SIZE];
    sdata[tid] = (idx < size) ? d_in[idx] : 0;
    // Boundary check
    if (idx >= size) {
        return;
    }
    // printf("idx: %d d_in: %f\n", idx, d_in[idx]);
    // Shared memory for this block

    // printf("size: %d tid: %d idx: %d sdata[8]: %f\n", size, tid, idx, sdata[8]);

    __syncthreads(); // Make sure the entire block is loaded!

    // Do reduction in shared mem
    for (unsigned int s = blockDim.x / 2; s > 0; s >>= 1) {
        if (tid < s) {
            // printf("s: %d tid: %d sdata[tid]: %f sdata[tid + s]: %f\n", s, tid, sdata[tid], sdata[tid + s]);
            // printf("sdata[8]: %f\n", sdata[8]);
            sdata[tid] += sdata[tid + s];
        }
        __syncthreads(); // Make sure all additions at one stage are done!
    }

    // Only thread 0 writes result for this block back to global mem
    if (tid == 0) {
        // printf("sdata[0]: %f\n", sdata[0]);
        d_out[blockIdx.x] = sdata[0];
        *res = -0.5 * sdata[0];
        *mc = total_weights + (-0.5) * (*res);
    }
}


void reduce_sum_float_h(float *d_in, float *out, unsigned int size) {
    // Set up execution parameters
    dim3 block(BLOCK_SIZE);
    dim3 grid((size + block.x - 1) / block.x);
    float *d_out, *d_tmp_in, *d_tmp_out;
    
    // Allocate device memory for the intermediate results
    cudaMalloc((void **)&d_tmp_in, size * sizeof(float));
    cudaMalloc((void **)&d_tmp_out, grid.x * sizeof(float));
    
    // Copy the input data to the temporary input buffer
    cudaMemcpy(d_tmp_in, d_in, size * sizeof(float), cudaMemcpyDeviceToDevice);
    
    while (size > 1) {
        // Call the reduction kernel
        reduce_sum_float<<<grid, block, block.x * sizeof(float)>>>(d_tmp_out, d_tmp_in, size);
        
        // Swap the input and output buffers for the next iteration
        d_out = d_tmp_in;
        d_tmp_in = d_tmp_out;
        d_tmp_out = d_out;
        
        // Calculate the size of the data for the next kernel launch
        size = (size + block.x - 1) / block.x;
        
        // Calculate the grid size for the next kernel launch
        grid.x = (size + block.x - 1) / block.x;
    }
    
    // Now that the data is small enough, perform the final reduction on the CPU
    float h_out;
    cudaMemcpy(&h_out, d_tmp_in, sizeof(float), cudaMemcpyDeviceToHost);
    
    *out = h_out;
    
    // Free the device memory
    cudaFree(d_tmp_in);
    cudaFree(d_tmp_out);
}

void reduce_sum_float_h(float *out, unsigned int size, float* d_tmp_in, float* d_tmp_out) {
    // Set up execution parameters
    dim3 block(BLOCK_SIZE);
    dim3 grid((size + block.x - 1) / block.x);
    float *d_out;
    
    // Allocate device memory for the intermediate results
    // cudaMalloc((void **)&d_tmp_in, size * sizeof(float));
    // cudaMalloc((void **)&d_tmp_out, grid.x * sizeof(float));
    
    // Copy the input data to the temporary input buffer
    // cudaMemcpy(d_tmp_in, d_in, size * sizeof(float), cudaMemcpyDeviceToDevice);
    
    while (size > 1) {
        // Call the reduction kernel
        reduce_sum_float<<<grid, block, block.x * sizeof(float)>>>(d_tmp_out, d_tmp_in, size);
        
        // Swap the input and output buffers for the next iteration
        d_out = d_tmp_in;
        d_tmp_in = d_tmp_out;
        d_tmp_out = d_out;
        
        // Calculate the size of the data for the next kernel launch
        size = (size + block.x - 1) / block.x;
        
        // Calculate the grid size for the next kernel launch
        grid.x = (size + block.x - 1) / block.x;
    }
    
    // Now that the data is small enough, perform the final reduction on the CPU
    float h_out;
    cudaMemcpy(&h_out, d_tmp_in, sizeof(float), cudaMemcpyDeviceToHost);
    
    *out = h_out;
    
    // Free the device memory
    // cudaFree(d_tmp_in);
    // cudaFree(d_tmp_out);
}

void reduce_sum_float_h(unsigned int size, float* d_tmp_in, float* d_tmp_out, float* energy, float* mc, float total_weights) {
    // Set up execution parameters
    dim3 block(BLOCK_SIZE);
    dim3 grid((size + block.x - 1) / block.x);
    float *d_out;
    
    // Allocate device memory for the intermediate results
    // cudaMalloc((void **)&d_tmp_in, size * sizeof(float));
    // cudaMalloc((void **)&d_tmp_out, grid.x * sizeof(float));
    
    // Copy the input data to the temporary input buffer
    // cudaMemcpy(d_tmp_in, d_in, size * sizeof(float), cudaMemcpyDeviceToDevice);
    
    while (size > 1) {
        // Call the reduction kernel
        reduce_sum_float_res<<<grid, block, block.x * sizeof(float)>>>(d_tmp_out, d_tmp_in, size, energy, mc, total_weights);
        
        // Swap the input and output buffers for the next iteration
        d_out = d_tmp_in;
        d_tmp_in = d_tmp_out;
        d_tmp_out = d_out;
        
        // Calculate the size of the data for the next kernel launch
        size = (size + block.x - 1) / block.x;
        
        // Calculate the grid size for the next kernel launch
        grid.x = (size + block.x - 1) / block.x;
    }
    
    // Free the device memory
    // cudaFree(d_tmp_in);
    // cudaFree(d_tmp_out);
}

#define CHECK_CUDA(func)                                                       \
{                                                                              \
    cudaError_t status = (func);                                               \
    if (status != cudaSuccess) {                                               \
        printf("CUDA API failed at line %d with error: %s (%d)\n",             \
               __LINE__, cudaGetErrorString(status), status);                  \
        return EXIT_FAILURE;                                                   \
    }                                                                          \
}

#define CHECK_CUSPARSE(func)                                                   \
{                                                                              \
    cusparseStatus_t status = (func);                                          \
    if (status != CUSPARSE_STATUS_SUCCESS) {                                   \
        printf("CUSPARSE API failed at line %d with error: %s (%d)\n",         \
               __LINE__, cusparseGetErrorString(status), status);              \
        return EXIT_FAILURE;                                                   \
    }                                                                          \
}

__global__ void calculate_sol(float* d_x, float* d_sol, int N) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < N) {
        d_sol[i] = (d_x[i] > 0) ? 1 : -1;
    }
}
// CUDA kernel to update vectors x and y
__global__ void update_vectors(float* d_x, float* d_y, float* d_jx, float p, float xi, int N, float dt) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < N) {
        // printf("1 i: %d dx: %f dy: %f\n", i, d_x[i], d_y[i]);
        float x = d_x[i];
        float y = d_y[i];
        y += ((-1 + p) * x + xi * d_jx[i]) * dt;
        x += y * dt;
        // printf("2 i: %d dx: %f dy: %f\n", i, x, y);
        y = (abs(x) > 1) ? 0 : y;
        x = min(max(x, -1.0f), 1.0f);
        d_x[i] = x;
        d_y[i] = y;
        // printf("3 i: %d dx: %f dy: %f\n", i, d_x[i], d_y[i]);
    }
}

// CUDA kernel to compute the energy
// Kernel to compute energy and mc
__global__ void compute_energy_s1(float* d_sol_J, float* d_sol, int N) {
  int i = blockIdx.x * blockDim.x + threadIdx.x;
  if(i < N){
    // printf("i: %d d_sol_J: %f\n", i, d_sol_J[i]);
    d_sol_J[i] = d_sol_J[i] * d_sol[i]; 
    // printf("2 i: %d d_sol_J: %f\n", i, d_sol_J[i]);
  }
}

// CUDA kernel to square a vector and save the result in another vector
__global__ void square_and_save(const float* d_v, float* d_squared, int nnz) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < nnz) {
        d_squared[i] = d_v[i] * d_v[i];
    }
}

__global__ void reduce_sum(float* d_v, float* d_sum, int nnz) {
    extern __shared__ float sdata[];
    int tid = threadIdx.x;
    int i = blockIdx.x * blockDim.x + tid;
    sdata[tid] = (i < nnz) ? d_v[i] : 0;
    __syncthreads();
    for (int s = blockDim.x / 2; s > 0; s >>= 1) {
        if (tid < s) {
            sdata[tid] += sdata[tid + s];
        }
        __syncthreads();
    }
    if (tid == 0) {
        d_sum[blockIdx.x] = sdata[0];
    }
}

// Main function
int main_pre(std::string s) {
    // Parameters
    int N;
    const int num_iters = 1000;
    const float dt = 0.75;
    // Load the sparse matrix from a COO file
    int nnz;
    int unit_length;
    int* cooRowInd;
    int* cooColInd;
    float* cooVal;


    std::ifstream file(s);
    if (!file.is_open()) {
        return 1;
    }
    std::cout << s;

    file >> unit_length >> nnz;

    cooRowInd = new int[nnz];
    cooColInd = new int[nnz];
    cooVal = new float[nnz];

    // Read the data
    for (int i = 0; i < nnz; i++) {
        file >> cooRowInd[i] >> cooColInd[i] >> cooVal[i];
        cooRowInd[i]--; // Adjust to 0-based indexing
        cooColInd[i]--;
        cooVal[i] = -cooVal[i]; // J = -J
    }

    //print "cooRowInd, cooColInd, cooVal"
    // std::cout << "print: " << std::endl;
    // for(int i = 0; i < nnz; i++){
    //     std::cout << cooRowInd[i] << " ";
    // }
    // std::cout << std::endl;
    // for(int i = 0; i < nnz; i++){
    //     std::cout << cooColInd[i] << " ";
    // }
    // std::cout << std::endl;
    // for(int i = 0; i < nnz; i++){
    //     std::cout << cooVal[i] << " ";
    // }
    // std::cout << std::endl;
    file.close();

    N = unit_length;

    int* cooRowInd_new;
    int* cooColInd_new;
    float* cooVal_new;
    int new_size;
    add_with_transpose(cooRowInd, cooColInd, cooVal, nnz, cooRowInd_new, cooColInd_new, cooVal_new, new_size);
    //print "cooColInd_new, cooVal_new, cooVal_new"
    // std::cout << "print: " << std::endl;
    // for(int i = 0; i < new_size; i++){
    //     std::cout << cooRowInd_new[i] << " ";
    // }
    // std::cout << std::endl;
    // for(int i = 0; i < new_size; i++){
    //     std::cout << cooColInd_new[i] << " ";
    // }
    // std::cout << std::endl;
    // for(int i = 0; i < new_size; i++){
    //     std::cout << cooVal_new[i] << " ";
    // }
    // std::cout << std::endl;
    // Allocate memory for vectors and matrix
    auto start_vec = std::chrono::system_clock::now();
    float *d_x, *d_y;
    cudaMalloc(&d_x, N * sizeof(float));
    cudaMalloc(&d_y, N * sizeof(float));
    cudaDeviceSynchronize();
    auto end_vec = std::chrono::system_clock::now();
    auto duration_vec = std::chrono::duration_cast<std::chrono::microseconds>(end_vec - start_vec);
    std::cout << ", " << double(duration_vec.count()) * std::chrono::microseconds::period::num / std::chrono::microseconds::period::den;
    // std::cout <<  "花费了"  << double(duration_vec.count()) * std::chrono::microseconds::period::num / std::chrono::microseconds::period::den << "秒" << std::endl;
    // Initialize x and y
    // For the sake of this example, we'll just set all elements to 1
    std::default_random_engine generator(std::chrono::system_clock::now().time_since_epoch().count());
    std::uniform_real_distribution<float> distribution(-0.1, 0.1);
    float* init_x = new float[N];
    float* init_y = new float[N];
    for (int i = 0; i < N; ++i) {
        init_x[i] = distribution(generator);
        init_y[i] = distribution(generator);
    }
    // print first 10 elements of the vector
    // std::cout << "print init_x: " << std::endl;
    // for(int i = 0; i < 10; i++){
    //     std::cout << init_x[i] << " ";
    // }
    // std::cout << std::endl;
    // std::cout << "print init_y: " << std::endl;
    // for(int i = 0; i < 10; i++){
    //     std::cout << init_y[i] << " ";
    // }
    // std::cout << std::endl;
    /*For debug fix x, y
    */
    // init_x[0] = -0.099998;
    // init_x[1] = 0.051121;
    // init_x[2] = 0.006553;
    // init_x[3] = -0.090591;
    // init_x[4] = 0.035859;
    // init_y[0] = 0.092351;
    // init_y[1] = 0.0800000;
    // init_y[2] = 0.070000;
    // init_y[3] = -0.060000;
    // init_y[4] = -0.032974;
    auto start_1 = std::chrono::system_clock::now();
    cudaMemcpy(d_x, init_x, N * sizeof(float), cudaMemcpyHostToDevice);
    cudaMemcpy(d_y, init_y, N * sizeof(float), cudaMemcpyHostToDevice);

    int* d_cooRowInd_new, *d_cooColInd_new;
    float* d_cooVal_new;
    cudaMalloc(&d_cooRowInd_new, new_size * sizeof(int));
    cudaMalloc(&d_cooColInd_new, new_size * sizeof(int));
    cudaMalloc(&d_cooVal_new, new_size * sizeof(float));
    cudaMemcpy(d_cooRowInd_new, cooRowInd_new, new_size * sizeof(int), cudaMemcpyHostToDevice);
    cudaMemcpy(d_cooColInd_new, cooColInd_new, new_size * sizeof(int), cudaMemcpyHostToDevice);
    cudaMemcpy(d_cooVal_new, cooVal_new, new_size * sizeof(float), cudaMemcpyHostToDevice);
    // Create cuSPARSE handle
    cusparseHandle_t handle;
    CHECK_CUSPARSE(cusparseCreate(&handle));
    // Create sparse matrix A in COO format
    // Convert COO to CSR
    
    cusparseSpMatDescr_t matA;

    // int* d_csrRowPtr;
    // cudaMalloc((void**)&d_csrRowPtr, sizeof(int)*(N+1));


    // Create the matrix descriptor
    CHECK_CUSPARSE(cusparseCreateCoo(&matA, N, N, new_size, d_cooRowInd_new, d_cooColInd_new, d_cooVal_new, CUSPARSE_INDEX_32I, CUSPARSE_INDEX_BASE_ZERO, CUDA_R_32F));
    cudaDeviceSynchronize();
    auto end_1 = std::chrono::system_clock::now();
    auto duration_pre = std::chrono::duration_cast<std::chrono::microseconds>(end_1 - start_1);
    std::cout << ", " << double(duration_pre.count()) * std::chrono::microseconds::period::num / std::chrono::microseconds::period::den;
    // std::cout <<  "花费了"  << double(duration_pre.count()) * std::chrono::microseconds::period::num / std::chrono::microseconds::period::den << "秒" << std::endl;
    // // Allocate host memory for CSR row pointers
    // int* csrRowPtr = new int[N+1];

    // // Copy CSR row pointers from device to host
    // cudaMemcpy(csrRowPtr, d_csrRowPtr, sizeof(int)*(N+1), cudaMemcpyDeviceToHost);

    // CHECK_CUSPARSE(cusparseCreateCoo(&matA0T, N, N, new_size, d_cooRowInd_new, d_cooColInd_new, d_cooVal_new, CUSPARSE_INDEX_32I, CUSPARSE_INDEX_BASE_ZERO, CUDA_R_32F));
    // // Create dense vector X and Y
    cusparseDnVecDescr_t vecX, vecY, vecSol, vecSolJ, vecJX;
    CHECK_CUSPARSE(cusparseCreateDnVec(&vecX, N, d_x, CUDA_R_32F));
    CHECK_CUSPARSE(cusparseCreateDnVec(&vecY, N, d_y, CUDA_R_32F));



    float* d_squared;
    cudaMalloc(&d_squared, new_size * sizeof(float));

    float total_weights;
    reduce_sum_float_h(d_cooVal_new, &total_weights, new_size);
    // std::cout << "Total weights: " << total_weights << std::endl;
    total_weights = -0.25 * total_weights;
    // std::cout << "Total weights: " << total_weights << std::endl;
    // Square the elements of J and save the result in d_squared
    square_and_save<<<(new_size + (BLOCK_SIZE - 1)) / BLOCK_SIZE, BLOCK_SIZE>>>(d_cooVal_new, d_squared, new_size);
    // cudaDeviceSynchronize();

    // Sum the squared elements
    float sum;
    reduce_sum_float_h(d_squared, &sum, new_size);
    // Calculate xi
    float xi = 0.5f / sqrt(sum / (N - 1));
    // std::cout << "xi: " << xi << std::endl;
    // Allocate an external buffer if needed

    // Allocate memory for energies and maxcut_values
    float *energies, *maxcut_values;
    energies = new float[num_iters];
    maxcut_values = new float[num_iters];
    float* d_sol;
    float *d_sol_J;
    float *d_jx;
    cudaMalloc(&d_sol, N * sizeof(float));
    cudaMalloc(&d_sol_J, N * sizeof(float));
    cudaMalloc(&d_jx, N * sizeof(float));
    CHECK_CUSPARSE(cusparseCreateDnVec(&vecSol, N, d_sol, CUDA_R_32F));
    CHECK_CUSPARSE(cusparseCreateDnVec(&vecSolJ, N, d_sol_J, CUDA_R_32F));
    CHECK_CUSPARSE(cusparseCreateDnVec(&vecJX, N, d_jx, CUDA_R_32F));

    // Allocate d_Jx to store the result of J @ x_comp
    float* d_Jx;  
    cudaMalloc(&d_Jx, N * sizeof(float));

    float alpha        = 1.0f;
    float beta         = 0.0f;
    void*                dBuffer    = NULL;
    size_t bufferSize = 0;
    CHECK_CUSPARSE( cusparseSpMV_bufferSize(
                                handle, CUSPARSE_OPERATION_NON_TRANSPOSE,
                                &alpha, matA, vecX, &beta, vecY, CUDA_R_32F,
                                CUSPARSE_SPMV_ALG_DEFAULT, &bufferSize) )
    CHECK_CUDA( cudaMalloc(&dBuffer, bufferSize) )
    // for(int i = 0; i < num_iters; i++){
    //     std::cout << i;
    //     if( i != num_iters - 1 )
    //         std::cout << ", ";
    // }
    // std::cout << std::endl;
    // float* hY = new float[5];
    auto start = std::chrono::system_clock::now();
    // float* d_tmp_in;
    float* d_tmp_out;
    // cudaMalloc((void **)&d_tmp_in, N * sizeof(float));
    cudaMalloc((void **)&d_tmp_out, ((N + (BLOCK_SIZE - 1)) / BLOCK_SIZE) * sizeof(float));

    float* energy_d;
    float* mc_d;
    cudaMalloc((void **)&energy_d, sizeof(float) * num_iters);
    cudaMalloc((void **)&mc_d, sizeof(float) * num_iters);
    for (int i = 0; i < num_iters; i++) {
        // std::cout << "ITERATION :" << i << std::endl;
        float p = (i == 0) ? 0.0 : (i * 1.0 / static_cast<float>(num_iters - 1));
            // allocate an external buffer if needed
        // printf("p: %f\n", p);
        // Perform SpMV: J @ x_comp
        CHECK_CUSPARSE(cusparseSpMV(handle, CUSPARSE_OPERATION_NON_TRANSPOSE, &alpha, matA, vecX, &beta, vecJX, CUDA_R_32F, CUSPARSE_SPMV_ALG_DEFAULT, dBuffer));
        // cudaDeviceSynchronize();
        // CHECK_CUDA( cudaMemcpy(hY, d_y, 5 * sizeof(float),
        //                    cudaMemcpyDeviceToHost) )
        //print hY
        // std::cout << "print hY: " << std::endl;
        // for(int j = 0; j < 5; j++){
        //     std::cout << hY[j] << " ";
        // }

        // Update x_comp and y_comp
        update_vectors<<<(N + (BLOCK_SIZE - 1)) / BLOCK_SIZE, BLOCK_SIZE>>>(d_x, d_y, d_jx, p, xi, N, dt);
        if(i == num_iters - 1){ // remove this if, you can get energy per iteration.
            calculate_sol<<<(N + (BLOCK_SIZE - 1)) / BLOCK_SIZE, BLOCK_SIZE>>>(d_x, d_sol, N);
            // Perform SpMV: sol.T @ J
            CHECK_CUSPARSE(cusparseSpMV(handle, CUSPARSE_OPERATION_NON_TRANSPOSE, &alpha, matA, vecSol, &beta, vecSolJ, CUDA_R_32F, CUSPARSE_SPMV_ALG_DEFAULT, dBuffer));
            // cudaDeviceSynchronize();
            // Compute the energy and maxcut_value
            compute_energy_s1<<<(N + (BLOCK_SIZE - 1)) / BLOCK_SIZE, BLOCK_SIZE>>>(d_sol_J, d_sol, N);
            reduce_sum_float_h(N, d_sol_J, d_tmp_out, &(energy_d[i]), &(mc_d[i]), total_weights);
        }
    }
    cudaDeviceSynchronize();
    auto end = std::chrono::system_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::microseconds>(end - start);
    // std::cout <<  "花费了" << double(duration.count()) * std::chrono::microseconds::period::num / std::chrono::microseconds::period::den << "秒" << std::endl;
    std::cout << ", " << double(duration.count()) * std::chrono::microseconds::period::num / std::chrono::microseconds::period::den;

    cudaMemcpy(energies, energy_d, sizeof(float) * num_iters, cudaMemcpyDeviceToHost);
    cudaMemcpy(maxcut_values, mc_d, sizeof(float) * num_iters, cudaMemcpyDeviceToHost);
    // for(int i = 0; i < num_iters; i++){
    //     std::cout << energies[i];
    //     if( i != num_iters - 1 )
    //         std::cout << ", ";
    // }
    // std::cout << std::endl;

    // for(int i = 0; i < num_iters; i++){
    //     std::cout << maxcut_values[i];
    //     if( i != num_iters - 1 )
    //         std::cout << ", ";
    // }
    // std::cout << std::endl;
    float* h_sol = new float[N];
    auto start_sol = std::chrono::system_clock::now();
    cudaMemcpy(h_sol, d_sol, N * sizeof(float), cudaMemcpyDeviceToHost);
    cudaDeviceSynchronize();
    auto end_sol = std::chrono::system_clock::now();
    auto duration_sol = std::chrono::duration_cast<std::chrono::microseconds>(end_sol - start_sol);
    // std::cout <<  "花费了" << double(duration_sol.count()) * std::chrono::microseconds::period::num / std::chrono::microseconds::period::den << "秒" << std::endl;
    std::cout << ", " << double(duration_sol.count()) * std::chrono::microseconds::period::num / std::chrono::microseconds::period::den << std::endl;
    // print first the vector
    // std::cout << "print h_sol: " << std::endl;
    // for(int i = 0; i < N; i++){
    //     std::cout << h_sol[i] << " ";
    // }
    // std::cout << std::endl;
// Cleanup
    cudaFree(d_x);
    cudaFree(d_y);
    cudaFree(d_cooRowInd_new);
    cudaFree(d_cooColInd_new);
    cudaFree(d_cooVal_new);
    cudaFree(dBuffer);
    cusparseDestroySpMat(matA);
    cusparseDestroyDnVec(vecX);
    cusparseDestroyDnVec(vecY);
    cusparseDestroy(handle);
    // cudaFree(d_tmp_in);
    cudaFree(d_tmp_out);
    delete[] init_x;
    delete[] init_y;
    delete[] cooRowInd;
    delete[] cooColInd;
    delete[] cooVal;
    return 0;
}

int main(){
    auto start_init = std::chrono::system_clock::now();
    cuInit(0);
    cudaDeviceSynchronize();
    auto end_init = std::chrono::system_clock::now();
    auto duration_init = std::chrono::duration_cast<std::chrono::microseconds>(end_init - start_init);
    // std::cout <<  "设备启动花费了"  << double(duration_init.count()) * std::chrono::microseconds::period::num / std::chrono::microseconds::period::den << "秒" << std::endl;
    std::cout << "name, copy vector, copy matrix, compute, copy result" << std::endl;
    for (int i = 1; i <= 67; i++) {
        std::string graph_file = "./Gset/G" + std::to_string(i);
        main_pre(graph_file);
    }
}