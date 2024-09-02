import numpy as np
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d import Axes3D
from scipy.interpolate import griddata

def plot_surface(non_zero_counts, title):
    num_blocks = non_zero_counts.shape[0]
    xy_index = np.arange(num_blocks)
    xx, yy = np.meshgrid(xy_index, xy_index)

    # Create a finer grid for interpolation
    fine_grid_x = np.linspace(0, num_blocks-1, 100)
    fine_grid_y = np.linspace(0, num_blocks-1, 100)
    fine_xx, fine_yy = np.meshgrid(fine_grid_x, fine_grid_y)

    # Interpolate the data
    fine_non_zero_counts = griddata((xx.flatten(), yy.flatten()), non_zero_counts.flatten(), (fine_xx, fine_yy), method='cubic')

    fig = plt.figure(figsize=(8, 6))
    axes = plt.axes(projection="3d")
    surf = axes.plot_surface(fine_xx, fine_yy, fine_non_zero_counts, rstride=1, cstride=1, cmap="rainbow")
    fig.colorbar(surf, ax=axes, shrink=0.5, aspect=5)
    axes.set_title(title)
    plt.savefig('./quantization/result/' + title)
    plt.show()

# Majority Red (High Numbers)
num_blocks = 4
non_zero_counts_high = np.zeros((num_blocks, num_blocks))
for i in range(num_blocks):
    for j in range(num_blocks):
        non_zero_counts_high[i, j] = np.random.randint(10, 16) if np.random.rand() > 0.2 else np.random.randint(0, 5)

plot_surface(non_zero_counts_high, "high energy state")


# Majority Blue (Low Numbers)
non_zero_counts_low = np.zeros((num_blocks, num_blocks))
for i in range(num_blocks):
    for j in range(num_blocks):
        non_zero_counts_low[i, j] = np.random.randint(0, 5) if np.random.rand() > 0.1 else np.random.randint(10, 16)

plot_surface(non_zero_counts_low, "low energy state")

# Half Red, Half Blue
non_zero_counts_half = np.zeros((num_blocks, num_blocks))
for i in range(num_blocks):
    for j in range(num_blocks):
        if (i + j) % 2 == 0:
            non_zero_counts_half[i, j] = np.random.randint(10, 16)
        else:
            non_zero_counts_half[i, j] = np.random.randint(0, 5)

plot_surface(non_zero_counts_half, "normal state")
