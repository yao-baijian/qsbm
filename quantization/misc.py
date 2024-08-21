import numpy as np
import matplotlib.pyplot as plt
import pandas as pd
from mpl_toolkits.mplot3d import Axes3D
from matplotlib.backends.backend_pdf import PdfPages
from misc import *

def load_data(name='G30'):
    file = open('./data/'+name, 'r')
    for (idx, line) in enumerate(file):
        if idx == 0:
            N = int(line.split(' ')[0])
            J = np.zeros([N,N])
        else:
            J[int(line.split(' ')[0])-1][int(line.split(' ')[1])-1] = (line.split(' ')[2])
            # J[int(line.split(' ')[0])-1][int(line.split(' ')[1])-1] = (line.split(' ')[2])
    file.close()
    tor_arr = -J
    return tor_arr

def plot_multi(iter,energies,save=False, name = 'G30'):

    x1 = np.linspace(0, iter + 1, iter + 1)
    y1 = energies

    plt.plot(x1, y1, label="energies", color="blue", linewidth=1)
    plt.xlabel("iteration_number")
    plt.ylabel("Ising energies")

    plt.title("Ising energies")
    plt.legend()
    plt.show()
    
    if save:
        plt.savefig(name + ".png")
        
def matrix_cnt(name = 'G34'):
    J = load_data(name)
    N = J.shape[0]
    new_size = (N//64 + 1) * 64
    matrix = np.resize(J, (new_size, new_size))  
    print(name,': ',np.count_nonzero(matrix))

def twod_loop(name = 'G34'):
    fig,axes = plt.subplots(figsize=(6,6))

    J = load_data(name)
    #生成整数矩阵
    N = J.shape[0]
    new_size = (N//64 + 1) * 64
    matrix = np.resize(J, (new_size, new_size))
    # print(new_size)
    # print(matrix)
    # np.savetxt('matrix.txt', matrix, fmt='%d')
    # 将矩阵转换为DataFrame对象
    # df = pd.DataFrame(matrix)
    # df0 = pd.DataFrame(J)
    # df.to_excel('matrix0.xlsx', index=False)
    # 将DataFrame对象保存到Excel文件中
    # df.to_excel('matrix.xlsx', index=False)

    #切割
    #small_matrix = matrix.reshape(new_size//64, 64, new_size//64, 64)

    num_blocks = new_size//64
    non_zero_counts = np.zeros((num_blocks, num_blocks))

    for i in range(num_blocks):
        for j in range(num_blocks):
            # 获取当前小矩阵的行和列索引
            row_start = i * 64
            row_end = row_start + 64
            col_start = j * 64
            col_end = col_start + 64

            # 计算当前小矩阵中的非零元素数量
            non_zero_counts[i, j] = np.count_nonzero(matrix[row_start:row_end, col_start:col_end])
            # print("Block ({}, {}): {} non-zero elements".format(i, j, non_zero_counts[i, j]))
            
    print(name)
    print(np.count_nonzero(matrix))
    x_index = np.arange(num_blocks)
    y_index = np.arange(num_blocks)

    xx, yy = np.meshgrid(x_index, y_index)

    # axes = plt.axes(projection= "3d")
    # axes.plot_surface(xx,yy,non_zero_counts,cmap = "rainbow")
    # axes.scatter(xx.flatten(),yy.flatten(), c=non_zero_counts.flatten(), cmap='Red')

    # axes.imshow(non_zero_counts)
    # 绘制点图，点的颜色越深，非零元素数量越多
    plt.scatter(yy.flatten(), xx.flatten(), marker='s',
                c=non_zero_counts.flatten(), cmap='Blues')

    # plt.colorbar()

    #判断
    #rows, cols = np.where(np.any(small_matrix != 0, axis=(1, 3)))
    #plt.scatter(cols, rows, s=1)

    # for i in range(len(non_zero_counts[i])):
    #     for j in range(len(non_zero_counts[i])):
    #         text = axes.text(i,j,non_zero_counts[i, j],
    #                          horizontalalignment = "center",
    #                          verticalalignment = "center"
    #                          )
    plt.plot()
    plt.savefig('result/'+ name + '.pdf') 
    plt.show()
 
def threed_loop(name = 'G34'):
    fig = plt.figure(figsize=(8,6))

    J = load_data(name)
    
    tile_size = 32
    #生成整数矩阵
    N = J.shape[0]
    new_size = (N//tile_size + 1) * tile_size
    matrix = np.resize(J, (new_size, new_size))
    print(new_size)
    # print(matrix)
    # np.savetxt('matrix.txt', matrix, fmt='%d')
    # 将矩阵转换为DataFrame对象
    # df = pd.DataFrame(matrix)
    # df0 = pd.DataFrame(J)
    # df.to_excel('matrix0.xlsx', index=False)
    # 将DataFrame对象保存到Excel文件中
    # df.to_excel('matrix.xlsx', index=False)

    #切割
    #small_matrix = matrix.reshape(new_size//64, 64, new_size//64, 64)

    num_blocks = new_size//tile_size
    non_zero_counts = np.zeros((num_blocks, num_blocks))

    for i in range(num_blocks):
        for j in range(num_blocks):
            # 获取当前小矩阵的行和列索引
            row_start = i * tile_size
            row_end = row_start + tile_size
            col_start = j * tile_size
            col_end = col_start + tile_size

            # 计算当前小矩阵中的非零元素数量
            non_zero_counts[i, j] = np.count_nonzero(matrix[row_start:row_end, col_start:col_end])
            print("Block ({}, {}): {} non-zero elements".format(i, j, non_zero_counts[i, j]))
            
            
    xy_index = np.arange(num_blocks)

    xx, yy = np.meshgrid(xy_index, xy_index)

    axes = plt.axes(projection= "3d")
    surf = axes.plot_surface(xx,yy,non_zero_counts, rstride = 1, cstride = 1, cmap = "rainbow")
    
    fig.colorbar(surf, ax = axes,
             shrink = 0.5, aspect = 5)
    # axes.scatter(xx.flatten(),yy.flatten(), c=non_zero_counts.flatten(), cmap='Blues')

    # axes.imshow(non_zero_counts)
    # 绘制点图，点的颜色越深，非零元素数量越多
    # plt.scatter(yy.flatten(), xx.flatten(), marker='s',
    #             c=non_zero_counts.flatten(), cmap='Blues')

    # plt.colorbar()

    #判断
    #rows, cols = np.where(np.any(small_matrix != 0, axis=(1, 3)))
    #plt.scatter(cols, rows, s=1)

    # for i in range(len(non_zero_counts[i])):
    #     for j in range(len(non_zero_counts[i])):
    #         text = axes.text(i,j,non_zero_counts[i, j],
    #                          horizontalalignment = "center",
    #                          verticalalignment = "center"
    #                          )
    plt.show()

def bar3d_loop(name = 'G34'):
    fig = plt.figure(figsize=(8,6))

    J = load_data(name)
    
    tile_size = 32
    #生成整数矩阵
    N = J.shape[0]
    new_size = (N//tile_size + 1) * tile_size
    matrix = np.resize(J, (new_size, new_size))
    print(new_size)
    # print(matrix)
    # np.savetxt('matrix.txt', matrix, fmt='%d')
    # 将矩阵转换为DataFrame对象
    # df = pd.DataFrame(matrix)
    # df0 = pd.DataFrame(J)
    # df.to_excel('matrix0.xlsx', index=False)
    # 将DataFrame对象保存到Excel文件中
    # df.to_excel('matrix.xlsx', index=False)

    #切割
    #small_matrix = matrix.reshape(new_size//64, 64, new_size//64, 64)

    num_blocks = new_size//tile_size
    non_zero_counts = np.zeros((num_blocks, num_blocks))

    for i in range(num_blocks):
        for j in range(num_blocks):
            # 获取当前小矩阵的行和列索引
            row_start = i * tile_size
            row_end = row_start + tile_size
            col_start = j * tile_size
            col_end = col_start + tile_size

            # 计算当前小矩阵中的非零元素数量
            non_zero_counts[i, j] = np.count_nonzero(matrix[row_start:row_end, col_start:col_end])
            print("Block ({}, {}): {} non-zero elements".format(i, j, non_zero_counts[i, j]))
            

    xy_index = np.arange(num_blocks)

    xx, yy = np.meshgrid(xy_index, xy_index)

    axes = plt.axes(projection= "3d")
    axes.bar3d(xx,yy,non_zero_counts, cmap = "rainbow")
    
    # fig.colorbar(surf, ax = axes,
    #          shrink = 0.5, aspect = 5)
    # axes.scatter(xx.flatten(),yy.flatten(), c=non_zero_counts.flatten(), cmap='Blues')

    # axes.imshow(non_zero_counts)
    # 绘制点图，点的颜色越深，非零元素数量越多
    # plt.scatter(yy.flatten(), xx.flatten(), marker='s',
    #             c=non_zero_counts.flatten(), cmap='Blues')

    # plt.colorbar()

    #判断
    #rows, cols = np.where(np.any(small_matrix != 0, axis=(1, 3)))
    #plt.scatter(cols, rows, s=1)

    # for i in range(len(non_zero_counts[i])):
    #     for j in range(len(non_zero_counts[i])):
    #         text = axes.text(i,j,non_zero_counts[i, j],
    #                          horizontalalignment = "center",
    #                          verticalalignment = "center"
    #                          )
    plt.show()
    
if __name__ == '__main__':
    
    data_list1 = ['G13', 'G20', 'G34', 'G53','G9', 'G40','G41', 'G47']
    data_list2 = ['G67', 'G70', 'G72', 'G77','G81']
    data_list3 = ['G13', 'G34', 'G19', 'G21','G20', 'G18', 'G51', 'G53', 'G54', 'G47', 'G40', 'G39', 'G42', 'G41', 'G9', 'G31']

    for name in data_list3:
        matrix_cnt(name)
        # twod_loop(name)
        # threed_loop(name)
        # bar3d_loop(name)
        
        
        
        
        
        
        
# fig,axes = plt.subplots(figsize=(8,6))
# J = load_data(name)
#生成整数矩阵
# N = J.shape[0]
# new_size = (N//64 + 1) * 64
# matrix = np.resize(J, (new_size, new_size))
# print(new_size)
# print(matrix)
# np.savetxt('matrix.txt', matrix, fmt='%d')
# 将矩阵转换为DataFrame对象
# df = pd.DataFrame(matrix)
# df0 = pd.DataFrame(J)
# df.to_excel('matrix0.xlsx', index=False)
# 将DataFrame对象保存到Excel文件中
# df.to_excel('matrix.xlsx', index=False)

#切割
#small_matrix = matrix.reshape(new_size//64, 64, new_size//64, 64)

# num_blocks = new_size//64
# non_zero_counts = np.zeros((num_blocks, num_blocks))

# for i in range(num_blocks):
#     for j in range(num_blocks):
        # 获取当前小矩阵的行和列索引
        # row_start = i * 64
        # row_end = row_start + 64
        # col_start = j * 64
        # col_end = col_start + 64

        # 计算当前小矩阵中的非零元素数量
#         non_zero_counts[i, j] = np.count_nonzero(matrix[row_start:row_end, col_start:col_end])
#         print("Block ({}, {}): {} non-zero elements".format(i, j, non_zero_counts[i, j]))
# x_index = np.arange(num_blocks)
# y_index = np.arange(num_blocks)

# xx, yy = np.meshgrid(x_index, y_index)

# axes = plt.axes(projection= "3d")
# axes.plot_surface(xx,yy,non_zero_counts,cmap = "rainbow")
# axes.scatter(xx.flatten(),yy.flatten(), c=non_zero_counts.flatten(), cmap='Red')

# axes.imshow(non_zero_counts)
# 绘制点图，点的颜色越深，非零元素数量越多
# plt.scatter(yy.flatten(), xx.flatten(), marker='s',
#             c=non_zero_counts.flatten(), cmap='Blues')

# plt.colorbar()

#判断
#rows, cols = np.where(np.any(small_matrix != 0, axis=(1, 3)))
#plt.scatter(cols, rows, s=1)

# for i in range(len(non_zero_counts[i])):
#     for j in range(len(non_zero_counts[i])):
#         text = axes.text(i,j,non_zero_counts[i, j],
#                          horizontalalignment = "center",
#                          verticalalignment = "center"
#                          )
# plt.show()
