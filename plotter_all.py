from plot_utils import run_chart_generation
import os
import pickle
import matplotlib.pyplot as plt
import numpy as np
import seaborn as sns
import pandas as pd
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
            10000
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

    if(max_is_done_percentage!=1.0):
        print(data)
        print(data["lastMovingTime[max]"].max().item())
    # if filter is empty, return Max time of data time
    if len(filter.time) == 0:
        return data["lastMovingTime[max]"].max().item()

        return data.time.max().item()
    else:
        # Get the time corresponding to the maximum isDonePercentage

        return data["lastMovingTime[max]"].max().item()
        return filter.time[0].item()


def plot_is_done_percentage_all(oracle, baseline, runtime, node: int, task_factor: float, ax=None, leader_based=False):
    print(f"Plotting isDonePercentage for node {node} and task factor {task_factor}")

    oracle = oracle.sel(totalNodes=node, totalTaskFactor=task_factor)
    baseline = baseline.sel(totalNodes=node, totalTaskFactor=task_factor)
    stable_oracle_time = extract_max_is_done_percentage(oracle)
    stable_baseline_time = baseline.where(baseline["isDonePercentage"] == 1.0, drop=True).time[0].item()
    
    # vertical line for oracle stable time
    ax.axvline(x=stable_oracle_time, color="black", linestyle="--", label="Oracle Stable Time")
    ax.axvline(x=stable_baseline_time, color="red", linestyle="--", label="Baseline Stable Time")
    for communication in runtime.communication:
        runtime.sel(totalNodes=node, totalTaskFactor=task_factor, communication=communication, leaderBased=leader_based)["isDonePercentage"].plot.line(x="time", label=f"Comm = {communication.item()}", ax=ax)
    
def plot_last_time_is_done_in_runtime_failure(oracle, baseline, runtime, node: int, task_factor: float, ax=None, leader_based=False):
    failure_times = [failure_time.item() for failure_time in oracle.failureTimeAverage]
    stable_oracle_times = []
    stable_baseline_times = []
    communications = [communication.item() for communication in runtime.communication]
    communications_stable_times = {communication: [] for communication in communications}
    del communications_stable_times[20]
    for i, failure_time in enumerate(failure_times):
        current_oracle = oracle.sel(totalNodes=node, totalTaskFactor=task_factor, failureTimeAverage=failure_time)
        current_oracle_stable_time = extract_max_is_done_percentage(current_oracle)
        current_baseline = baseline.sel(totalNodes=node, totalTaskFactor=task_factor, failureTimeAverage=failure_time)
        current_baseline_stable_time = extract_max_is_done_percentage(current_baseline)
        stable_oracle_times.append(current_oracle_stable_time)
        stable_baseline_times.append(current_baseline_stable_time)
        for communication in communications_stable_times:
            current_runtime = runtime.sel(
                totalNodes=node, totalTaskFactor=task_factor, failureTimeAverage=failure_time, communication=communication, leaderBased=leader_based
            )
            current_runtime_stable_time = extract_max_is_done_percentage(current_runtime)
            communications_stable_times[communication].append(current_runtime_stable_time)

    # do a scatter plot of fauliture times vs various stable times
    ax.plot(failure_times, stable_oracle_times, color="black", label="Oracle Stable Time")
    ax.plot(failure_times, stable_baseline_times, color="red", label="Baseline Stable Time", marker="x")
    for communication in communications_stable_times:
        ax.plot(failure_times, communications_stable_times[communication], label=f"Comm = {communication}", marker="x")    


def plot_bar_plot_percentage_time(oracle, baseline, runtime, node: int, task_factor: float, ax=None):
    
    failure_times = 5000.0
    current_oracle = oracle.sel(totalNodes=node, totalTaskFactor=task_factor, failureTimeAverage=failure_times)
    current_oracle_stable_time = extract_max_is_done_percentage(current_oracle)
    
    # Get all communication values
    communications = [comm.item() for comm in runtime.communication.values if comm.item() != 20]
    
    # Create data for plotting
    data = []
    
    # Add baseline data (same for all communication values)
    current_baseline = baseline.sel(totalNodes=node, totalTaskFactor=task_factor, failureTimeAverage=failure_times)
    current_baseline_stable_time = extract_max_is_done_percentage(current_baseline) / current_oracle_stable_time
    for comm in communications:
        data.append({'Communication': str(comm), 'Approach': 'Baseline', 'Time Percentage': current_baseline_stable_time})
    
    # Add leader-based and gossip data for each communication value
    for comm in communications:
        # Leader-based
        current_leader_based = runtime.sel(
            totalNodes=node, totalTaskFactor=task_factor, failureTimeAverage=failure_times, 
            communication=comm, leaderBased=True
        )
        leader_time = extract_max_is_done_percentage(current_leader_based) / current_oracle_stable_time
        data.append({'Communication': str(comm), 'Approach': 'Leader Based', 'Time Percentage': leader_time})
        
        # Gossip
        current_gossip = runtime.sel(
            totalNodes=node, totalTaskFactor=task_factor, failureTimeAverage=failure_times, 
            communication=comm, leaderBased=False
        )
        gossip_time = extract_max_is_done_percentage(current_gossip) / current_oracle_stable_time
        data.append({'Communication': str(comm), 'Approach': 'Gossip', 'Time Percentage': gossip_time})
    
    # Create DataFrame
    df = pd.DataFrame(data)
    
    # Create grouped bar plot
    sns.barplot(
        data=df,
        x='Communication',
        y='Time Percentage',
        hue='Approach',
        ax=ax
    )
    
    # Adjust legend
    if ax.get_legend():
        ax.legend(title='Approach')
def create_grid_plot_base(plot_func, data_sources, x_label, y_label, plot_title, filename, extra_args=None):
    """
    Generic grid plotting function to reduce code duplication
    """
    if extra_args is None:
        extra_args = {}
    
    # Get dimensions from first data source
    first_data_key = list(data_sources.keys())[0]
    nodes = data_sources[first_data_key]["mean"].totalNodes
    task_factors = data_sources[first_data_key]["mean"].totalTaskFactor

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
            plot_func(
                *[data_sources[key]["mean"] for key in data_sources],
                int(node.item()),
                float(task_factor.item()),
                ax=ax,
                **extra_args
            )
            
            # Add title to each subplot
            ax.set_title(f"Node {int(node.item())}, Task Factor {float(task_factor.item())}")
            
            # Only add x-label to bottom row
            if i == n_rows - 1:
                ax.set_xlabel(x_label)
            
            # Only add y-label to leftmost column
            if j == 0:
                ax.set_ylabel(y_label)
            
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
    create_grid_plot_base(
        plot_func=plot_is_done_percentage_all,
        data_sources={"oracle": data_loaded["oracle"], "baseline": data_loaded["baseline"], "runtime": data_loaded["runtime"]},
        x_label="Time",
        y_label="Is Done Percentage",
        plot_title=plot_title,
        filename=filename,
        extra_args={"leader_based": leader_based}
    )

def create_grid_plot_failure(leader_based, plot_title, filename):
    create_grid_plot_base(
        plot_last_time_is_done_in_runtime_failure,
        {"oracle": data_loaded["oracle-random-failure"], 
         "baseline": data_loaded["baseline-random-failure"], 
         "runtime": data_loaded["runtime-random-failure"]},
        "Average Failure Time",
        "Stable Time",
        plot_title,
        filename,
        {"leader_based": leader_based}
    )

def create_bar_plot(plot_title, filename):
    create_grid_plot_base(
        plot_bar_plot_percentage_time,
        {"oracle": data_loaded["oracle-random-failure"], 
         "baseline": data_loaded["baseline-random-failure"], 
         "runtime": data_loaded["runtime-random-failure"]},
        "Approach",
        "Percentage of Time",
        plot_title,
        filename,
    )


create_bar_plot(
    plot_title="Percentage of Time Across Approaches", 
    filename="bar_plot.png"
)
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
