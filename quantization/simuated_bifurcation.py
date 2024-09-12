import math
import numpy as np
from misc import *


def SB(sb_type, J, init_x, init_y, num_iters, dt):
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
        if (sb_type == "bsb"):
            y_comp += ((-1 + alpha[i]) * x_comp + xi * (J @ x_comp)) * dt 
            x_comp += y_comp * dt 
            y_comp[np.abs(x_comp) > 1] = 0.
            x_comp = np.clip(x_comp,-1, 1)
        elif (sb_type == "dsb"):
        # dsb
            y_comp += ((-1 + alpha[i]) * x_comp + xi * (J @ x_comp.sign())) * dt
            x_comp += y_comp * dt
            y_comp[x_comp.abs() > 1] = 0.
            x_comp.clamp_(-1, 1)
        elif (sb_type == "sb"):    
        # sb
            y_comp += xi * (J @ x_comp) * dt # y momentum
            # for j in range(M):
            #     y_comp += ((-1 + alpha[i]) * x_comp - x_comp ** 3) * dt / M # alpha equals to alpha
            #     x_comp += y_comp * dt / M #

        sol = np.sign(x_comp)
        e = - 1 / 2 * sol.T @ J @ sol
        energy = -1/4 * J.sum() - 1/2 * e
        energies.append(energy)

    return energies

def qSB(J, init_x, init_y, num_iters, best_known = 0, factor = [8,4,4], q = -127):
    scale = 1/127
    N = J.shape[0]
    x_comp = (init_x.copy()) //scale # position
    y_comp = (init_y.copy()) //scale # momentum
    energies = []
    alpha = (np.linspace(0, 1, num_iters)) //scale
    quantized_range = 2 ^ (factor[0] - 1) - 1
    step = num_iters
    acc_reach = 0
    
    for i in range(num_iters):

        # y_comp += ((-1 + alpha[i]) * x_comp + xi * (J @ x_comp)) * dt 
        # x_comp += y_comp * dt 
        # y_comp[np.abs(x_comp) > 1] = 0.
        # x_comp = np.clip(x_comp,-1, 1)
    
        y_comp_div_dt = (q + alpha[i]) * x_comp + scaleup(np.array(J @ x_comp), factor[0])
        y_comp = y_comp + scaledown_rightshift(y_comp_div_dt, factor[1])
        x_comp = x_comp + scaledown_rightshift(y_comp, factor[2])
        
        y_comp[np.abs(x_comp) > quantized_range] = 0.
        x_comp = np.clip(x_comp, -quantized_range, quantized_range)
        
        sol = np.sign(x_comp)
        e = - 1 / 2 * sol.T @ J @ sol  #
        energy = -1/4 * J.sum() - 1/2 * e
        if (energy > best_known * 0.99) and acc_reach == 0:
            acc_reach = 1
            step = i
        energies.append(energy)
        # if i == num_iters -1:
            # print(-1/4 * J.sum() - 1/2 * e)
            # return -1/4 * J.sum() - 1/2 * e
    return np.array(energies), step

def qSB_improve(J, 
                init_x, 
                init_y, 
                num_iters, 
                best_known = 0, 
                factor = [6, 4 ,4], 
                qtz_type = 'scaleup'):

    energies = []
    scale = 2 ** factor[0] - 1
    N = J.shape[0]
    xi = (0.7 / math.sqrt(N))
    
    # x_comp = (init_x.copy()) * scale
    # y_comp = (init_y.copy()) * scale
    alpha = np.linspace(0, 1, num_iters)
    
    step = num_iters
    acc_reach = 0
    
    x_comp = scaleup(np.array(init_x.copy()), 127)
    y_comp = scaleup(np.array(init_y.copy()), 127)
    
    x_comp_init = x_comp.copy() 
    
    for i in range(num_iters):
        '''
        Note:
        1. All scale up to match with x_comp, the intuitive is to avoid generate any decimal during calculation, 
            and keep Lagrange ratio unchanged
            
        2. Only scale up x_comp and y_comp, scale down when calculating. This will generate decimal during calculation
        
        3. Uniformly quantization, dont scale up x_comp and y_comp, just match them to quantized decimal.
        
            y_comp += ((-1 + alpha[i]) * x_comp + xi * (J @ x_comp)) * dt
            x_comp += y_comp * dt
            y_comp[np.abs(x_comp) > 1] = 0.
            x_comp = np.clip(x_comp,-1, 1)
        '''
        
        if (qtz_type == 'scaleup'):
            y_comp_div_dt = (-scale + alpha[i] * scale) * x_comp + scaleup(np.array(J @ x_comp) * xi, scale)
            y_comp = y_comp + scaledown(y_comp_div_dt, factor[1])
            x_comp = x_comp + scaledown(y_comp, factor[2])
        elif(qtz_type == 'unscale'):
            y_comp_div_dt = (-1 + alpha[i]) * x_comp + np.array(J @ x_comp) * xi
            y_comp = y_comp + scaledown(y_comp_div_dt,  factor[1])
            x_comp = x_comp + scaledown(y_comp, factor[2])
        elif(qtz_type=='uni'):
            # TODO
            # need implement uniform quantization here
            x_comp = x_comp + scaledown(y_comp, factor[2])
        
        y_comp[np.abs(x_comp) > scale] = 0.
        x_comp = np.clip(x_comp, -scale, scale)
        
        sol = np.sign(x_comp)
        e = - 1 / 2 * sol.T @ J @ sol  #
        energy = -1/4 * J.sum() - 1/2 * e
        if (energy > best_known * 0.99) and acc_reach == 0:
            acc_reach = 1
            step = i
        energies.append(energy)
        # if i == num_iters -1:
            # print(-1/4 * J.sum() - 1/2 * e)
            # return -1/4 * J.sum() - 1/2 * e
    # zero_momentum = 0
    # for y in y_comp:
    #     if y == 0.:
    #         zero_momentum += 1
    # print("total zero y", zero_momentum)
    return np.array(energies), step, x_comp_init

def scaleup(targets, factor):
    rescaled_targets = []
    for target in targets:
        rescaled_targets.append(int(target * factor))
    return np.array(rescaled_targets)

def scaledown(targets, factor):
    rescaled_targets = []
    for target in targets:
        rescaled_targets.append(int(target/factor))
    return np.array(rescaled_targets)
    
def scaledown_rightshift(targets, factor):
    rescaled_targets = []
    for target in targets:
        rescaled_targets.append(int(target) >> factor)
    return np.array(rescaled_targets)



