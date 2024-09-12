from misc import *
import matplotlib.pyplot as plt
from scipy.ndimage import gaussian_filter
from simuated_bifurcation import *
import time

if __name__ == '__main__':
    data_list   = {'G9': 2054, 
                   'G47': 6634, 
                   'G39': 2356, 
                   'G42': 2394}
    
    qsb_type = ['improve', 'scaleup']
    sb_type  = "bsb"
    quant_index = [7, 6, 5]
    # quant_index = [7]
    num_iter = 1000
    dt = 0.25
        
    for set_name, best_known in data_list.items():
        J = load_data(set_name)        
        J = (J.T + J)
        init_x = np.random.uniform(-0.1,0.1,J.shape[0])
        init_y = np.random.uniform(-0.1,0.1,J.shape[0])

        vex_return = init_x.copy()
        # for scaleup 
        # factor = [7, 128,4],  [8, 16, 16], [7,16,16]
        # for noscale
        # factor = [7, 4 ,4],  [8, 4, 4]
        qsb_energies = []
        if (qsb_type[0] == 'improve') :
            for qi in quant_index:
                fc = [qi, 16, 16]
                qsb_energy, qsb_step = qSB_improve(J, init_x, init_y, num_iter, best_known, factor = fc, qtz_type=qsb_type[1])
                qsb_energies.append(gaussian_filter(qsb_energy, sigma=3))
        elif (qsb_type[0] == 'non-converge'):
            fc = [7, 4, 4]
            qsb_energy, qsb_step = qSB_improve(J, init_x, init_y, num_iter, best_known, factor = fc, qtz_type='scaleup')
            qsb_energies.append(gaussian_filter(qsb_energy, sigma=3))
        else:
            qsb_energy, qsb_step = qSB(J, init_x, init_y, num_iter, best_known)
            
            
        bsb_energy = gaussian_filter(SB(sb_type, J, init_x, init_y, num_iter, dt), sigma=3) 

        
        print(set_name, ", steps: ", qsb_step)
        
        plt.xlabel('iterations')
        plt.ylabel('Ising Energy')
        plt.axvline(x=qsb_step, color='grey', linestyle='--',linewidth=0.5, label='TTS')
        
        idx = 0
        for qsb_energy in qsb_energies:
            plt.plot(qsb_energy,label='qSB (n=' + str(quant_index[idx]) + ')'+ set_name)
            idx += 1
            
        plt.plot(bsb_energy,label='bSB ' + set_name)
        plt.legend()
        plt.savefig('./quantization/result/set_'+ set_name+'_ising_solution.pdf')
        plt.show()
        
        
    # data_list = ['G9', 'G15', 'G30', 'G22','G36']
    # for name in data_list:
    #     J = load_data(name)
    #     # J = init_data()
    #     N = J.shape[0]

        # N = 800
        # J = np.random.randint(0, 2, (N, N)) * 2 - 1. #OK
        # J = (J.T + J) / 2

        # dt = 0.025
        # num_iter = 1000

        # J = torch.from_numpy(np.array([[0.,-1.],[-1., 0.]]))    # init_x = torch.from_numpy(np.random.uniform(-0.1,0.1,J.shape[0]))
        # init_y = torch.from_numpy(np.random.uniform(-0.1,0.1,J.shape[0]))
        # init_x = np.random.uniform(-0.1,0.1,J.shape[0])
        # init_y = np.random.uniform(-0.1,0.1,J.shape[0])
        # init_x = (torch.empty([N, 1]).uniform_(-0.1, 0.1)).float()
        # init_y = torch.empty([N, 1]).uniform_(-0.1, 0.1).float()
        #
        # t = time.perf_counter()
        # energies = np.array(simulated_bifurcation(J,init_x,init_y,num_iter,dt))
        # print(f'coast:{time.perf_counter() - t:.8f}s')
        # plt.xlabel('iterations')
        # plt.ylabel('Ising Energy')
        # plt.plot(energies)
    
        # plt.savefig('ising_solution.pdf')
        # plt.show()
        
    # TODO add extra large
    # sets        = ['800', '1000', '2000']
    # data_list   = [{'G9': 2054, 'G13': 567, 'G19': 872, 'G20': 908},
    #                {'G47': 6634, 'G51': 3808, 'G53': 3814, 'G54': 3804}, 
    #                {'G39': 2356, 'G40': 2308, 'G41': 2314, 'G42': 2394}]

    # data_list   = [{'G9': 2054, 'G13': 567, 'G19': 872, 'G20': 908}]
    
    
    # i = 0
    # for group in data_list:
    #     plt.xlabel('iterations')
    #     plt.ylabel('Ising Energy')
    #     for set_name, best_known in group.items():
    #         J = load_data(set_name)
    #         # J = init_data()
    #         N = J.shape[0]
    #         dt = 0.25
    #         energy, step = cal_energy(J, best_known)
    #         print(set_name, ", steps: ", step)
            
    #         smooth_energy = gaussian_filter(energy, sigma=1)
    #         plt.axvline(x=step, color='grey', linestyle='--',linewidth=0.5, label='TTS')
    #         plt.plot(smooth_energy,label=set_name)
            
    #     plt.legend()
    #     plt.savefig('set_'+ sets[i]+'_ising_solution.pdf')
    #     plt.show()
    #     i+=1