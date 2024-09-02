from misc import *
import matplotlib.pyplot as plt
from scipy.ndimage import gaussian_filter
from simuated_bifurcation import *
import time
from mpl_toolkits.axes_grid1.inset_locator import inset_axes, mark_inset

if __name__ == '__main__':
    data_list = {'G9': 2054}
    
    qsb_type = ['non-converge', 'scaleup']
    sb_type = "bsb"
    quant_index = [7, 6, 5]
    num_iter = 1000
    dt = 0.25
        
    for set_name, best_known in data_list.items():
        J = load_data(set_name)        
        J = (J.T + J)
        init_x = np.random.uniform(-0.1, 0.1, J.shape[0])
        init_y = np.random.uniform(-0.1, 0.1, J.shape[0])

        non_con_qsb_energy, qsb_step = qSB_improve(J, init_x, init_y, num_iter, best_known, factor=[7, 4, 4], qtz_type='scaleup')
        static_qsb_energy, qsb_step = qSB_improve(J, init_x, init_y, num_iter, best_known, factor=[7, 32, 32], qtz_type='unscale')
        con_qsb_energy, qsb_step = qSB_improve(J, init_x, init_y, num_iter, best_known, factor=[7, 16, 16], qtz_type='scaleup')
            
        non_con_qsb_energy = gaussian_filter(non_con_qsb_energy[0:800], sigma=20) 
        static_qsb_energy = gaussian_filter(static_qsb_energy[0:800], sigma=20) 
        con_qsb_energy = gaussian_filter(con_qsb_energy[0:800], sigma=20) 

        print(set_name, ", steps: ", qsb_step)
        plt.figure(figsize=(10, 6))
        plt.xlabel('iterations')
        plt.ylabel('Ising Energy')
        # plt.axvline(x=qsb_step, color='grey', linestyle='--', linewidth=0.5, label='TTS')
        
        iter = np.arange(num_iter)[0:800]
        plt.plot(iter, non_con_qsb_energy, label='over dumped non-converge qSB')
        plt.plot(iter, static_qsb_energy, label='no dumped qSB')
        plt.plot(iter, con_qsb_energy, label='converge qSB')

        # Set the x-axis limits of the main plot
        plt.xlim(0, num_iter)

        plt.legend()
        plt.savefig('./quantization/result/set_' + set_name + '_ising_solution_with_zoom.png')
        plt.show()