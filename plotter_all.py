from plot_utils import run_chart_generation
import os
import pickle
import matplotlib.pyplot as plt
import numpy as np
import seaborn as sns
experiments = [
    "baseline", "baseline-random-failure",
    "oracle", "oracle-random-failure",
    "runtime",
    "runtime-random-failure"
]

def load_is_not_in_cache(experiment):
    print(f"Loading {experiment} data from cache...")
    cache_path = f"cache/{experiment}.pkl"
    if not os.path.exists("cache"):
        os.makedirs("cache")
    if os.path.exists(cache_path):
        with open(cache_path, "rb") as f:
            data = pickle.load(f)
    else:
        data = run_chart_generation(
            f"data/{experiment}",
            [experiment],
            '{: 0.3f}',
            100,
            "time",
            ["seed"],
            0,
            5000
        )
        with open(cache_path, "wb") as f:
            pickle.dump(data, f)

    return data

data_loaded = {
    experiment: load_is_not_in_cache(experiment) for experiment in experiments
}

# Helper function to create a multi-panel plot
def plot_dimension_comparison(ds, variable, dim_to_plot, other_dims, final_time=None):
    """
    Create plots comparing a variable across one dimension with separate panels for combinations of other dimensions
    
    Parameters:
    - ds: xarray Dataset
    - variable: str, name of the variable to plot
    - dim_to_plot: str, dimension to plot on x-axis
    - other_dims: list, dimensions to use for panel separation
    - final_time: int or None, if specified, only plot this time step
    """
    if final_time is not None:
        # Select the specific time point
        ds_plot = ds.sel(time=final_time, method='nearest')
    else:
        # Use the last time point
        ds_plot = ds.isel(time=-1)
    
    # Create combinations for the plot panels
    dim1, dim2 = other_dims
    values1 = ds_plot[dim1].values
    values2 = ds_plot[dim2].values
    
    # Set up the plot grid
    fig, axes = plt.subplots(len(values1), len(values2), figsize=(4*len(values2), 3*len(values1)), 
                            sharex=True, sharey=True)
    
    # Adjust the main title spacing
    fig.suptitle(f'{variable} vs {dim_to_plot} (Time: {ds_plot.time.values})', fontsize=16, y=0.98)
    
    # Creating subplots for each combination
    for i, val1 in enumerate(values1):
        for j, val2 in enumerate(values2):
            # Select data for this subplot
            data_subset = ds_plot.sel({dim1: val1, dim2: val2})
            
            # Get the current axis
            if len(values1) > 1 and len(values2) > 1:
                ax = axes[i, j]
            elif len(values1) > 1:
                ax = axes[i]
            elif len(values2) > 1:
                ax = axes[j]
            else:
                ax = axes
            
            # Plot the data
            data_subset[variable].plot(x=dim_to_plot, ax=ax)
            
            # Set titles and labels
            ax.set_title(f'{dim1}={val1}, {dim2}={val2}')
            ax.set_xlabel(dim_to_plot)
            ax.set_ylabel(variable)
            ax.grid(True, linestyle='--', alpha=0.7)
    
    plt.tight_layout(rect=[0, 0, 1, 0.96])  # Adjust for the main title
    return fig

# Function to plot time evolution for different parameter combinations
def plot_time_evolution(ds, variable, dims_to_compare, fixed_dims=None):
    """
    Plot the time evolution of a variable for different combinations of dimensions
    
    Parameters:
    - ds: xarray Dataset
    - variable: str, name of the variable to plot
    - dims_to_compare: list of 2 dimensions to vary and compare
    - fixed_dims: dict, dimensions to keep fixed with their values
    """
    # Apply fixed dimensions if provided
    if fixed_dims:
        ds_plot = ds.sel(fixed_dims)
    else:
        ds_plot = ds
    
    dim1, dim2 = dims_to_compare
    values1 = ds_plot[dim1].values
    values2 = ds_plot[dim2].values
    
    plt.figure(figsize=(12, 8))
    
    colors = plt.cm.viridis(np.linspace(0, 1, len(values1)))
    line_styles = ['-', '--', '-.', ':'] * (len(values2)//4 + 1)
    
    # Plot each combination
    for i, val1 in enumerate(values1):
        for j, val2 in enumerate(values2):
            # Select data for this combination
            selector = {dim1: val1, dim2: val2}
            data_subset = ds_plot.sel(selector)
            
            # Plot the time series
            plt.plot(data_subset.time, data_subset[variable], 
                    color=colors[i], linestyle=line_styles[j],
                    label=f'{dim1}={val1}, {dim2}={val2}')
    
    # Add title and labels
    fixed_dims_str = ', '.join([f'{k}={v}' for k, v in (fixed_dims or {}).items()])
    title = f'Time evolution of {variable}'
    if fixed_dims_str:
        title += f' ({fixed_dims_str})'
    
    plt.title(title)
    plt.xlabel('Time')
    plt.ylabel(variable)
    plt.grid(True, linestyle='--', alpha=0.7)
    plt.legend(bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.tight_layout()
    
    return plt.gcf()
# Function to create grid of time evolution plots for different combinations of parameters
def plot_time_evolution_grid(ds, variable):
    """
    Create a comprehensive grid of time evolution plots for different parameter combinations
    
    Parameters:
    - ds: xarray Dataset
    - variable: str, name of the variable to plot
    
    Returns:
    - Dictionary of figure objects, keyed by parameter combination
    """
    figures = {}
    
    # Get all parameter values
    leader_values = ds.leaderBased.values
    node_values = ds.totalNodes.values
    
    # 1. For each totalNodes, compare communication and totalTaskFactor
    for leader in leader_values:
        for node in node_values:
            fig, ax = plt.subplots(figsize=(12, 8))
            
            # Get parameter values
            comm_values = ds.communication.values
            task_values = ds.totalTaskFactor.values
            
            # Create color map and line styles
            colors = plt.cm.viridis(np.linspace(0, 1, len(comm_values)))
            line_styles = ['-', '--', '-.', ':'] * (len(task_values)//4 + 1)
            
            # Plot each combination
            for i, comm in enumerate(comm_values):
                for j, task in enumerate(task_values):
                    # Select data for this combination
                    data_subset = ds.sel(leaderBased=leader, totalNodes=node, 
                                       communication=comm, totalTaskFactor=task)
                    
                    # Plot the time series
                    ax.plot(data_subset.time, data_subset[variable], 
                          color=colors[i], linestyle=line_styles[j],
                          label=f'comm={comm}, taskFactor={task}')
            
            # Add title and labels
            plt.title(f'{variable} Over Time (leaderBased={leader}, totalNodes={node})')
            plt.xlabel('Time')
            plt.ylabel(variable)
            plt.grid(True, linestyle='--', alpha=0.7)
            plt.legend(bbox_to_anchor=(1.05, 1), loc='upper left')
            plt.tight_layout()
            
            figures[f'leader_{leader}_nodes_{node}'] = fig
    
    return figures
# Function to create a heatmap for two dimensions
def create_heatmap(ds, variable, dims_to_compare, fixed_dims=None, time_point=-1):
    """
    Create a heatmap of a variable for two dimensions
    
    Parameters:
    - ds: xarray Dataset
    - variable: str, name of the variable to plot
    - dims_to_compare: list of 2 dimensions to use for the heatmap axes
    - fixed_dims: dict, dimensions to keep fixed with their values
    - time_point: int, time index to plot (-1 for final time point)
    """
    # Select the time point
    ds_time = ds.isel(time=time_point)
    
    # Apply fixed dimensions if provided
    if fixed_dims:
        ds_plot = ds_time.sel(fixed_dims)
    else:
        ds_plot = ds_time
    
    dim1, dim2 = dims_to_compare
    
    # Create the plot
    plt.figure(figsize=(10, 8))
    
    # Extract the plotted coordinates for proper labeling
    x_vals = ds_plot[dim1].values
    y_vals = ds_plot[dim2].values
    
    # Create the heatmap data
    heatmap_data = ds_plot[variable].sel({dim1: x_vals, dim2: y_vals})
    
    # Plot the heatmap
    sns.heatmap(heatmap_data, annot=True, fmt=".2f", cmap="viridis", 
                xticklabels=x_vals, yticklabels=y_vals)
    
    # Add title and labels
    fixed_dims_str = ', '.join([f'{k}={v}' for k, v in (fixed_dims or {}).items()])
    title = f'Heatmap of {variable} ({dim1} vs {dim2})'
    if fixed_dims_str:
        title += f' ({fixed_dims_str})'
    
    plt.title(title)
    plt.xlabel(dim1)
    plt.ylabel(dim2)
    plt.tight_layout()
    
    return plt.gcf()

def plot_time_evolution_by_nodes(ds, variable, dims_to_compare, totalNodes_values=None):
    """
    Create separate time evolution plots for each totalNodes value
    
    Parameters:
    - ds: xarray Dataset
    - variable: str, name of the variable to plot
    - dims_to_compare: list of 2 dimensions to vary and compare
    - totalNodes_values: list of totalNodes values to plot (if None, use all available values)
    
    Returns:
    - List of figure objects
    """
    # Get all totalNodes values if not specified
    if totalNodes_values is None:
        totalNodes_values = ds.totalNodes.values
    
    figures = []
    
    # Create a plot for each totalNodes value
    for node_value in totalNodes_values:
        # Select data for this node value
        ds_node = ds.sel(totalNodes=node_value)
        
        dim1, dim2 = dims_to_compare
        values1 = ds_node[dim1].values
        values2 = ds_node[dim2].values
        
        # Create figure
        fig, ax = plt.subplots(figsize=(12, 8))
        
        # Create color map and line styles
        colors = plt.cm.viridis(np.linspace(0, 1, len(values1)))
        line_styles = ['-', '--', '-.', ':'] * (len(values2)//4 + 1)
        
        # Plot each combination
        for i, val1 in enumerate(values1):
            for j, val2 in enumerate(values2):
                # Select data for this combination
                selector = {dim1: val1, dim2: val2}
                data_subset = ds_node.sel(selector)
                
                # Plot the time series
                ax.plot(data_subset.time, data_subset[variable], 
                      color=colors[i], linestyle=line_styles[j],
                      label=f'{dim1}={val1}, {dim2}={val2}')
        
        # Add title and labels
        plt.title(f'Time evolution of {variable} (totalNodes={node_value})')
        plt.xlabel('Time')
        plt.ylabel(variable)
        plt.grid(True, linestyle='--', alpha=0.7)
        plt.legend(bbox_to_anchor=(1.05, 1), loc='upper left')
        plt.tight_layout()
        
        figures.append(fig)
    
    return figures



def plot_completion_speed_comparison(ds, output_dir):
    """
    Create a plot that compares task completion speed across different node sizes
    
    Parameters:
    - ds: xarray Dataset
    - output_dir: directory to save plots
    """
    # Get values to compare
    leader_values = ds.leaderBased.values
    node_values = ds.totalNodes.values
    
    # Create figures for 50% completion time comparison
    for leader in leader_values:
        fig, ax = plt.subplots(figsize=(12, 8))
        
        # Define the threshold for "completion" (50%)
        threshold = 50.0
        
        # Create arrays to store results
        comm_values = ds.communication.values
        task_values = ds.totalTaskFactor.values
        time_to_complete = np.zeros((len(node_values), len(comm_values), len(task_values)))
        
        # For each combination, find the time when isDonePercentage exceeds threshold
        for n, node in enumerate(node_values):
            for c, comm in enumerate(comm_values):
                for t, task in enumerate(task_values):
                    subset = ds.sel(leaderBased=leader, totalNodes=node, 
                                  communication=comm, totalTaskFactor=task)
                    
                    # Find first time when isDonePercentage exceeds threshold
                    times = subset.time.values
                    done_pct = subset.isDonePercentage.values
                    
                    # Find the first index where isDonePercentage exceeds threshold
                    indices = np.where(done_pct > threshold)[0]
                    if len(indices) > 0:
                        time_to_complete[n, c, t] = times[indices[0]]
                    else:
                        time_to_complete[n, c, t] = np.nan  # Not completed in the time window
        
        # Create bar chart for each node value
        bar_width = 0.15
        x = np.arange(len(comm_values))
        
        for n, node in enumerate(node_values):
            offset = bar_width * (n - len(node_values)/2 + 0.5)
            for t, task in enumerate(task_values):
                if t == 1:  # Only plot the middle task factor value (1.0) for clarity
                    ax.bar(x + offset, time_to_complete[n, :, t], width=bar_width, 
                         label=f'Nodes={node}')
        
        # Add labels and legend
        ax.set_xlabel('Communication Range')
        ax.set_ylabel('Time to 50% Completion')
        ax.set_title(f'Time to 50% Completion by Node Count (leaderBased={leader}, taskFactor=1.0)')
        ax.set_xticks(x)
        ax.set_xticklabels(comm_values)
        ax.legend()
        ax.grid(True, linestyle='--', alpha=0.7)
        
        # Save figure
        plt.tight_layout()
        fig.savefig(f'{output_dir}/completion_speed_leader_{leader}.png', 
                  dpi=300, bbox_inches='tight')
        
        # Create heatmap version for all combinations
        for n, node in enumerate(node_values):
            fig, ax = plt.subplots(figsize=(10, 8))
            
            # Create heatmap of time to 50% completion
            im = ax.imshow(time_to_complete[n], cmap='viridis_r', aspect='auto')
            
            # Add colorbar
            cbar = ax.figure.colorbar(im, ax=ax)
            cbar.ax.set_ylabel('Time to 50% Completion', rotation=-90, va="bottom")
            
            # Set tick labels
            ax.set_xticks(np.arange(len(task_values)))
            ax.set_yticks(np.arange(len(comm_values)))
            ax.set_xticklabels(task_values)
            ax.set_yticklabels(comm_values)
            
            # Add labels
            ax.set_xlabel('Total Task Factor')
            ax.set_ylabel('Communication Range')
            ax.set_title(f'Time to 50% Completion (leaderBased={leader}, totalNodes={node})')
            
            # Loop over data dimensions and create text annotations
            for i in range(len(comm_values)):
                for j in range(len(task_values)):
                    value = time_to_complete[n, i, j]
                    text = ax.text(j, i, f'{value:.1f}',
                                 ha="center", va="center", color="w")
            
            plt.tight_layout()
            fig.savefig(f'{output_dir}/completion_heatmap_leader_{leader}_nodes_{node}.png', 
                      dpi=300, bbox_inches='tight')
            
# Create output directory for plots
import os
output_dir = 'xarray_plots'
os.makedirs(output_dir, exist_ok=True)
ds = data_loaded["baseline"][0]["baseline"]
fig4 = plot_time_evolution(
    ds, 'isDonePercentage',
    {'totalNodes': 20.0, 'totalTaskFactor': 2.0}
)
#fig4.savefig(f'{output_dir}/time_evolution_leader_true.png', dpi=300, bbox_inches='tight')

ds = data_loaded["runtime"][0]["runtime"]
# 1. Plot isDonePercentage vs communication for different combinations of leaderBased and totalNodes
#fig1 = plot_dimension_comparison(
#    ds, 'isDonePercentage', 'communication', 
#    ['leaderBased', 'totalNodes'], 
#    final_time=None
#)
#fig1.savefig(f'{output_dir}/isDonePercentage_vs_communication.png', dpi=300, bbox_inches='tight')

# 2. Plot isDonePercentage vs totalTaskFactor for different combinations of leaderBased and totalNodes
#fig2 = plot_dimension_comparison(
#    ds, 'isDonePercentage', 'totalTaskFactor', 
#    ['leaderBased', 'communication'], 
#    final_time=None
#)
#fig2.savefig(f'{output_dir}/isDonePercentage_vs_totalTaskFactor.png', dpi=300, bbox_inches='tight')

# 3. Plot isDonePercentage vs totalNodes for different combinations of leaderBased and communication
#fig3 = plot_dimension_comparison(
#    ds, 'isDonePercentage', 'totalNodes', 
#    ['leaderBased', 'totalTaskFactor'], 
#    final_time=None
#)
#fig3.savefig(f'{output_dir}/isDonePercentage_vs_totalNodes.png', dpi=300, bbox_inches='tight')

# 4. Time evolution for selected parameters
# For leader-based = True
#fig4 = plot_time_evolution(
#    ds, 'isDonePercentage',
#    ['communication', 'totalTaskFactor'],
#    {'leaderBased': True, 'totalNodes': 20.0}
#)
#fig4.savefig(f'{output_dir}/time_evolution_leader_true.png', dpi=300, bbox_inches='tight')

# For leader-based = False
fig5 = plot_time_evolution(
    ds, 'isDonePercentage',
    ['communication', 'totalTaskFactor'],
    {'leaderBased': False, 'totalNodes': 20.0}
)
fig5.savefig(f'{output_dir}/time_evolution_leader_false.png', dpi=300, bbox_inches='tight')

# 5. Create heatmaps for the final state
# For leader-based = True
#fig6 = create_heatmap(
#    ds, 'isDonePercentage',
#    ['communication', 'totalTaskFactor'],
#    {'leaderBased': True, 'totalNodes': 20.0}
#)
#fig6.savefig(f'{output_dir}/heatmap_leader_true.png', dpi=300, bbox_inches='tight')

# For leader-based = False
#fig7 = create_heatmap(
#    ds, 'isDonePercentage',
#    ['communication', 'totalTaskFactor'],
#    {'leaderBased': False, 'totalNodes': 20.0}
#)
#fig7.savefig(f'{output_dir}/heatmap_leader_false.png', dpi=300, bbox_inches='tight')

grid_figures = plot_time_evolution_grid(ds, 'isDonePercentage')
print(f"All plots have been saved to the '{output_dir}' directory.")
#plot_completion_speed_comparison(ds, output_dir)