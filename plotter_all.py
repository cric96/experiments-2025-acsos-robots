from plot_utils import run_chart_generation
import os
import pickle

experiments = [
    "baseline", "baseline-random-failure",
    "oracle", "oracle-random-failure",
    #"runtime",
    #"runtime-random-failure"
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