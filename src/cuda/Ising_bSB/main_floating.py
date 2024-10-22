import torch
import math
import numpy as np
from datashape import int8
import time
import matplotlib.pyplot as plt
import random
def load_data():
    file = open('./Gset/G1', 'r')
    for (idx, line) in enumerate(file):
        if idx == 0:
            N = int(line.split(' ')[0])
            J = np.zeros([N,N])
        else:
            J[int(line.split(' ')[0])-1][int(line.split(' ')[1])-1] = (line.split(' ')[2])
            # J[int(line.split(' ')[0]) - 1][int(line.split(' ')[1]) - 1] = random.randint(0, 5)
    file.close()
    # tor_arr = -J
    tor_arr = torch.from_numpy(-J).float()
    return tor_arr


def bSB(J, init_x, init_y, num_iters, dt):
    N = J.shape[0]
    x_comp = init_x.clone()
    y_comp = init_y.clone()

    # scaling of the matrix.
    xi = 0.5 / J.square().sum().div_(N - 1).sqrt_()
    print("xi value", xi)

    # pump rate is linearly increased from 0 to 1.
    ps = torch.linspace(0, 1, num_iters)

    energies = []
    maxcut_values = []
    for i in range(num_iters):
        Jx = (J @ x_comp)
        # print("iterations", i)
        # print("1 x value", x_comp)
        # print("1 y value", y_comp)
        #  print ps[i]
        print("ps", ps[i])
        y_comp += ((-1 + ps[i]) * x_comp + xi * Jx) * dt
        x_comp += y_comp * dt
        # print("2 x value", x_comp)
        # print("2 Y value", y_comp)
        y_comp[x_comp.abs() > 1] = 0.
        x_comp.clamp_(-1, 1)
        # print("3 x value", x_comp)
        # print("3 Y value", y_comp)
        # compute the energy.
        sol = x_comp.sign()
        solJ = J @ sol
        # print("solJ: ", solJ)
        e = - 1 / 2 * sol.T @ solJ
        mc = total_weights + (-1 / 2) * e

        energies.append(e)
        maxcut_values.append(mc)

        if i == num_iters -1:
            print(e)
            print(mc)
        # print("e: ", e)
        # print("mc", mc)
    print(sol)
    return energies,maxcut_values


def dSB(J, init_x, init_y, num_iters, dt):
    N = J.shape[0]
    x_comp = init_x.clone()
    y_comp = init_y.clone()

    # scaling of the matrix.
    xi = 0.5 / J.square().sum().div_(N - 1).sqrt_()
    print("xi value",xi)

    # pump rate is linearly increased from 0 to 1.
    ps = torch.linspace(0, 1, num_iters)

    energies = []
    for i in range(num_iters):
        y_comp += ((-1 + ps[i]) * x_comp + xi * (J @ x_comp.sign())) * dt
        x_comp += y_comp * dt

        y_comp[x_comp.abs() > 1] = 0.
        x_comp.clamp_(-1, 1)

        # compute the energy.
        sol = x_comp.sign()
        e = - 1 / 2 * sol.T @ J @ sol
        mc = total_weights + (-1 / 2) * e

        energies.append(e)

        if i == num_iters -1:
            print(e)
            print(mc)

    return energies


def plot(iter,energies):

    # 设置x,y轴的数值
    x1 = np.linspace(0, iter+1, iter+1)
    y1 = energies

    # 在当前绘图对象中画图（x轴,y轴,给所绘制的曲线的名字，画线颜色，画线宽度）
    plt.plot(x1, y1, label="energies", color="blue", linewidth=1)
    # plt.plot(x1, y2, label="$cos(x)$", color="red", linewidth=2)

    # X和Y坐标轴的表示
    plt.xlabel("iteration_number")
    plt.ylabel("Ising energies")

    # 图表的标题
    plt.title("Ising energies")
    # Y轴的范围
    # plt.ylim(-1.5, 1.5)
    # 显示图示
    plt.legend()
    # 显示图
    plt.show()
    # 保存图
    # plt.savefig("C:\Users\lanmage2\Desktop\sin&cos.png")

if __name__ == '__main__':
    J = load_data()
    # J = init_data()
    N = J.shape[0]

    # N = 800
    # J = np.random.randint(0, 2, (N, N)) * 2 - 1. #OK
    J = (J.T + J)

    dt = 0.75
    num_iter = 1000
    total_weights = (-J).sum() / 4
    # print("Total weights", total_weights)

    init_x = torch.empty([N, 1]).uniform_(-0.1, 0.1)
    init_y = torch.empty([N, 1]).uniform_(-0.1, 0.1)
    #For debug, fix init_x and init_y
    # init_x = torch.tensor([-0.099998, 0.051121, 0.006553, -0.090591, 0.035859])
    # init_y = torch.tensor([0.092351, 0.0800000, 0.070000, -0.060000, -0.032974])
    #print first 10 elements of the vector
    # print("init_x: ", init_x)
    # print("init_y: ", init_y)
    (energies,maxcut_values) = np.array(bSB(J,init_x,init_y,num_iter,dt))
    # print energies and maxcut_values
    print("energies: ", energies)
    print("maxcut_values: ", maxcut_values)
    plt.xlabel('iterations')
    plt.ylabel('Value')
    plt.plot(energies,label = "Ising Energy")
    plt.plot(maxcut_values,label='Max-Cut Value')
    plt.legend()
    plt.show()

