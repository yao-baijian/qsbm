import math
import numpy as np
from misc import *

scale = 1/127
def simulated_bifurcation(sb_type, J, init_x, init_y, num_iters, dt):
    N = J.shape[0]
    x_comp = (init_x.copy()) # position
    y_comp = (init_y.copy()) # momentum
    xi = (0.7 / math.sqrt(N)) # xi = 0.5
    sol = np.sign(x_comp)
    energies = []
    e = - 1 / 2 * sol.T @ J @ sol
    alpha = (np.linspace(0, 1, num_iters)) 
    for i in range(num_iters):
        
        # bsb
        y_comp += ((-1 + alpha[i]) * x_comp + xi * (J @ x_comp)) * dt 
        x_comp += y_comp * dt 
        y_comp[np.abs(x_comp) > 1] = 0.
        np.clip(x_comp,-1, 1)
    
    # dsb
    # y_comp += ((-1 + alpha[i]) * x_comp + xi * (J @ x_comp.sign())) * dt
    # x_comp += y_comp * dt
    # y_comp[x_comp.abs() > 1] = 0.
    # x_comp.clamp_(-1, 1)

    # sb
    # y_comp += xi * (J @ x_comp) * dt # y momentum
    # for j in range(M):
    #     y_comp += ((-1 + alpha[i]) * x_comp - x_comp ** 3) * dt / M # alpha equals to alpha
    #     x_comp += y_comp * dt / M #

        sol = np.sign(x_comp)
        e = - 1 / 2 * sol.T @ J @ sol
        energy = -1/4 * J.sum() - 1/2 * e
        energies.append(energy)

    return energies

def qSB(J, init_x, init_y, num_iters, dt, best_known = 0, factor = [8,3,3], q = -127):
    N = J.shape[0]
    x_comp = (init_x.copy()) //scale # position
    y_comp = (init_y.copy()) //scale # momentum
    xi = 0.5 / np.sqrt(((np.square(J).sum())/(N - 1))) // scale
    energies = []
    alpha = (np.linspace(0, 1, num_iters)) //scale
    
    step = num_iters
    acc_reach = 0
    for i in range(num_iters):
        # bsb

        # y_comp += ((-1 + alpha[i]) * x_comp + xi * (J @ x_comp)) * dt  # update the momentum
        mv = np.array(J @ x_comp)
        mv_list = []
        for elem in mv:
            mv_list.append(int(elem) * factor[0])
        mv = np.array(mv_list)
        temp = (q + alpha[i]) * x_comp + mv
        temp_list = []
        for elem in temp:
            temp_list.append(int(elem) >> factor[1])
        temp = np.array(temp_list)
        y_comp = y_comp + temp

        # x_comp = x_comp + y_comp * dt  # update the x position part
        temp1_list = []
        for elem in y_comp:
            temp1_list.append(int(elem) >> factor[2])
        temp1 = np.array(temp1_list)
        x_comp = x_comp + temp1
        y_comp[np.abs(x_comp) > 127] = 0.

        x_comp = np.clip(x_comp,-127, 127)
        sol = np.sign(x_comp)
        e = - 1 / 2 * sol.T @ J @ sol  #
        # energies.append(e)
        
        energy = -1/4 * J.sum() - 1/2 * e
        if (energy > best_known*0.99) and acc_reach == 0:
            acc_reach = 1
            step = i
        energies.append(energy)
        # if i == num_iters -1:
            # print(-1/4 * J.sum() - 1/2 * e)
            # return -1/4 * J.sum() - 1/2 * e
    return np.array(energies), step

def cal_energy(J, best_known = 0):
    N = J.shape[0]
    J = (J.T + J) 
    num_iter = 1000
    dt = 0.25
    init_x = np.random.uniform(-0.1,0.1,J.shape[0])
    init_y = np.random.uniform(-0.1,0.1,J.shape[0])
    qsb_energy, qsb_step = qSB(J,init_x,init_y, num_iter,dt,best_known)
    dsb_energy = simulated_bifurcation(J,init_x,init_y, num_iter,dt)
    return qsb_energy, qsb_step, dsb_energy



