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
        mean, std = run_chart_generation(
            f"data/{experiment}",
            [experiment],
            '{: 0.3f}',
            100,
            "time",
            ["seed"],
            0,
            5000
        )
        data = {
            "mean": mean[experiment],
            "std": std[experiment]
        }
        with open(cache_path, "wb") as f:
            pickle.dump(data, f)

    return data

data_loaded = {
    experiment: load_is_not_in_cache(experiment) for experiment in experiments
}

def extract_max_is_done_percentage(data):
    # Extract the maximum isDonePercentage value
    max_is_done_percentage = data["isDonePercentage"].max().item()
    filter = data.where(data["isDonePercentage"] == max_is_done_percentage, drop=True)
    # if filter is empty, return Max time of data time
    if len(filter.time) == 0:
        max_time = data.time.max().item()
        return max_time
    else:
        # Get the time corresponding to the maximum isDonePercentage
        max_time = filter.time[0].item()
        # Create a new DataArray with the same dimensions as the original data
        max_is_done_percentage = data.isDonePercentage.where(data.isDonePercentage == max_is_done_percentage, drop=True)
        # Return the maximum isDonePercentage value and its corresponding time
        return max_time

    # Create a new DataArray with the same dimensions as the original data
    print(max_is_done_percentage)
def plot_is_done_percentage_all(oracle, baseline, runtime, node: int, task_factor: float, ax=None, leader_based=False):
    print(f"Plotting isDonePercentage for node {node} and task factor {task_factor}")

    oracle = oracle.sel(totalNodes=node, totalTaskFactor=task_factor)
    baseline = baseline.sel(totalNodes=node, totalTaskFactor=task_factor)
    stable_oracle_time = oracle.where(oracle["isDonePercentage"] == 1.0, drop=True).time[0].item()
    stable_baseline_time = baseline.where(baseline["isDonePercentage"] == 1.0, drop=True).time[0].item()
    # vertical line for oracle stable time
    ax.axvline(x=stable_oracle_time, color="black", linestyle="--", label="Oracle Stable Time")
    ax.axvline(x=stable_baseline_time, color="red", linestyle="--", label="Baseline Stable Time")
    for communication in runtime.communication:
        runtime.sel(totalNodes=node, totalTaskFactor=task_factor, communication=communication, leaderBased=leader_based)["isDonePercentage"].plot.line(x="time", label=f"Comm = {communication.item()}", ax=ax)
    
def plot_last_time_is_done_in_runtime_failure(oracle, baseline, runtime, node: int, task_factor: float, ax=None):
    failure_times = [failure_time.item() for failure_time in oracle.failureTimeAverage]
    stable_oracle_times = []
    stable_baseline_times = []
    communications = [communication.item() for communication in runtime.communication]
    communications_stable_times = {communication: [] for communication in communications}
    del communications_stable_times[20]
    for i, failure_time in enumerate(failure_times):
        current_oracle = oracle.sel(totalNodes=node, totalTaskFactor=task_factor, failureTimeAverage=failure_time)
        current_oracle_stable_time = extract_max_is_done_percentage(current_oracle)
        #ax.axvline(x=current_oracle_stable_time, color="black", linestyle="--", label=f"Oracle Stable Time = {current_failure}")
        current_baseline = baseline.sel(totalNodes=node, totalTaskFactor=task_factor, failureTimeAverage=failure_time)
        current_baseline_stable_time = extract_max_is_done_percentage(current_baseline)
        stable_oracle_times.append(current_oracle_stable_time)
        stable_baseline_times.append(current_baseline_stable_time)
        for communication in communications_stable_times:
            current_runtime = runtime.sel(totalNodes=node, totalTaskFactor=task_factor, failureTimeAverage=failure_time, communication=communication)
            current_runtime_stable_time = extract_max_is_done_percentage(current_runtime)
            communications_stable_times[communication].append(current_runtime_stable_time)
            #ax.axvline(x=current_runtime_stable_time, color="red", linestyle="--", label=f"Runtime Stable Time = {current_failure}")

     # do a scatter plot of fauliture times vs various stable times
    ax.scatter(failure_times, stable_oracle_times, color="black", label="Oracle Stable Time")
    ax.scatter(failure_times, stable_baseline_times, color="red", label="Baseline Stable Time")
    for communication in communications_stable_times:
        ax.scatter(failure_times, communications_stable_times[communication], label=f"Comm = {communication}", marker="x")    
def create_grid_plot_failure(leader_based, plot_title, filename):
    nodes = data_loaded["oracle-random-failure"]["mean"].totalNodes
    task_factors = data_loaded["oracle-random-failure"]["mean"].totalTaskFactor
    n_rows = len(nodes)
    n_cols = len(task_factors)
    fig, axes = plt.subplots(n_rows, n_cols, figsize=(n_cols * 4, n_rows * 3), sharex=True, sharey=True)
    # Handle the case where axes might be 1D
    if n_rows == 1 and n_cols == 1:
        axes = np.array([[axes]])
    elif n_rows == 1:
        axes = axes.reshape(1, -1)
    elif n_cols == 1:
        axes = axes.reshape(-1, 1)
    # Loop through the grid and fill each subplot
    for i, node in enumerate(nodes):
        for j, task_factor in enumerate(task_factors):
            ax = axes[i, j]
            plot_last_time_is_done_in_runtime_failure(
                data_loaded["oracle-random-failure"]["mean"],
                data_loaded["baseline-random-failure"]["mean"],
                data_loaded["runtime-random-failure"]["mean"],
                int(node.item()),
                float(task_factor.item()),
                ax=ax,
            )
            
            # Add title to each subplot
            ax.set_title(f"Node {int(node.item())}, Task Factor {float(task_factor.item())}")
            
            # Only add x-label to bottom row
            if i == n_rows - 1:
                ax.set_xlabel("Failure Time")
            
            # Only add y-label to leftmost column
            if j == 0:
                ax.set_ylabel("Is Done Percentage")
            
            ax.grid(True)
            
            # Remove individual legends
            if ax.get_legend():
                ax.get_legend().remove()
    
    # Add a single legend for the entire figure
    handles, labels = axes[0, 0].get_legend_handles_labels()
    fig.legend(handles, labels, loc='upper center', bbox_to_anchor=(0.5, 0.05), ncol=3)
    # Adjust layout
    plt.tight_layout(rect=[0, 0.1, 1, 0.95])
    plt.suptitle(plot_title, fontsize=16)
    # Create directory if it doesn't exist
    os.makedirs("xarray_plots", exist_ok=True)
    # Save the figure
    plt.savefig(f"xarray_plots/{filename}")
    plt.close()

def create_grid_plot(leader_based, plot_title, filename):
    nodes = data_loaded["oracle"]["mean"].totalNodes
    task_factors = data_loaded["oracle"]["mean"].totalTaskFactor

    n_rows = len(nodes)
    n_cols = len(task_factors)
    fig, axes = plt.subplots(n_rows, n_cols, figsize=(n_cols * 4, n_rows * 3), sharex=True, sharey=True)

    # Handle the case where axes might be 1D
    if n_rows == 1 and n_cols == 1:
        axes = np.array([[axes]])
    elif n_rows == 1:
        axes = axes.reshape(1, -1)
    elif n_cols == 1:
        axes = axes.reshape(-1, 1)

    # Loop through the grid and fill each subplot
    for i, node in enumerate(nodes):
        for j, task_factor in enumerate(task_factors):
            ax = axes[i, j]
            plot_is_done_percentage_all(
                data_loaded["oracle"]["mean"],
                data_loaded["baseline"]["mean"],
                data_loaded["runtime"]["mean"],
                int(node.item()),
                float(task_factor.item()),
                ax=ax,
                leader_based=leader_based
            )
            
            # Add title to each subplot
            ax.set_title(f"Node {int(node.item())}, Task Factor {float(task_factor.item())}")
            
            # Only add x-label to bottom row
            if i == n_rows - 1:
                ax.set_xlabel("Time")
            
            # Only add y-label to leftmost column
            if j == 0:
                ax.set_ylabel("Is Done Percentage")
            
            ax.grid(True)
            
            # Remove individual legends
            if ax.get_legend():
                ax.get_legend().remove()

    # Add a single legend for the entire figure
    handles, labels = axes[0, 0].get_legend_handles_labels()
    fig.legend(handles, labels, loc='upper center', bbox_to_anchor=(0.5, 0.05), ncol=3)

    # Adjust layout
    plt.tight_layout(rect=[0, 0.1, 1, 0.95])
    plt.suptitle(plot_title, fontsize=16)

    # Create directory if it doesn't exist
    os.makedirs("xarray_plots", exist_ok=True)

    # Save the figure
    plt.savefig(f"xarray_plots/{filename}")
    plt.close()

# Create gossip plot
create_grid_plot(
    leader_based=False,
    plot_title="Is Done Percentage Across Nodes and Task Factors - Gossip", 
    filename="is_done_percentage_grid_gossip.png"
)

# Create leader plot
create_grid_plot(
    leader_based=True,
    plot_title="Is Done Percentage Across Nodes and Task Factors - Leader", 
    filename="is_done_percentage_grid_leader.png"
)

create_grid_plot_failure(
    leader_based=False,
    plot_title="Is Done Percentage Across Failure Times - Gossip", 
    filename="is_done_percentage_grid_gossip_failure.png"
)

create_grid_plot_failure(
    leader_based=True,
    plot_title="Is Done Percentage Across Failure Times - Leader", 
    filename="is_done_percentage_grid_leader_failure.png"
)
