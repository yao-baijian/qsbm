from misc import *
from simuated_bifurcation import *
import sys
import json

if __name__ == '__main__':
    filename = sys.argv[1]
    best_known = int(sys.argv[2])
    sb_type  = sys.argv[3]
    num_iter = int(sys.argv[4])
    qsb_type = ['non-converge', 'scaleup']
    quant_index = [7, 6, 5]
    dt = 0.25
    J = load_data(filename)
    J = (J.T + J)
    init_x = np.random.uniform(-0.1,0.1,J.shape[0])
    init_y = np.random.uniform(-0.1,0.1,J.shape[0])
    # for scaleup
    # factor = [7, 128,4],  [8, 16, 16], [7,16,16]
    # for noscale
    # factor = [7, 4 ,4],  [8, 4, 4]
    if (qsb_type[0] == 'improve') :
        for qi in quant_index:
            fc = [qi, 16, 16]
            qsb_energy, qsb_step, x_comp_init = qSB_improve(J, init_x, init_y, num_iter, best_known, factor = fc, qtz_type=qsb_type[1])
    elif (qsb_type[0] == 'non-converge'):
        fc = [7, 4, 4]
        qsb_energy, qsb_step, x_comp_init = qSB_improve(J, init_x, init_y, num_iter, best_known, factor = fc, qtz_type='scaleup')
    else:
        qsb_energy, qsb_step = qSB(J, init_x, init_y, num_iter, best_known)
    bsb_energy = SB(sb_type, J, init_x, init_y, num_iter, dt)

    print(','.join(map(str, x_comp_init)))
    print(','.join(map(str, qsb_energy)))
