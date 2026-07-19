# citi-technology-sde

## Overview

This project builds a set of tools for working with live market data, starting with
collecting data over time and building toward making that data easier to understand.

## Query data from the web

This project contains a Java application that queries the latest price of `DIA` from the
Twelve Data API every 15 seconds and stores each result in a queue.

## Project structure

```
.
├── src
│   └── App.java                    Java application source
├── data
│   └── prices.jsonl                 Collected price data, written by App.java (not committed)
├── DIA_Price_Visualization.ipynb    Notebook that plots data/prices.jsonl
├── run.sh                           Loads .env, compiles, and runs App.java
├── .env.example                     Template for the required environment variable
└── README.md
```

## Prerequisites

A free Twelve Data account and API key are required. An account can be created at
[twelvedata.com](https://twelvedata.com/), and an API key can be generated from the
account dashboard.

Copy `.env.example` to `.env` and set the key:

```
TWELVE_DATA_API_KEY=your_api_key_here
```

`.env` is listed in `.gitignore` and is never committed.

## How the application works

`src/App.java` is a single class with the following responsibilities.

**HTTP requests**

The application uses `java.net.http.HttpClient`, which is part of the standard library
in Java 11, so no external dependencies are needed. Each request is sent to:

```
https://api.twelvedata.com/price?symbol=DIA&apikey=API_KEY
```

**Response parsing**

The API returns a small JSON object such as `{"price":"420.15"}`. Since the response
shape is fixed and simple, the price is extracted with a regular expression rather than
pulling in a JSON library.

**Storage**

Each result is captured as a `PricePoint`, a small internal class holding the price and
the timestamp it was retrieved at. Points are stored in a `java.util.Queue` backed by an
`ArrayDeque`, in the order they arrive, and are also appended as a line of JSON to
`data/prices.jsonl` (JSON Lines format — one `{"price":...,"timestamp":...}` object per
line) so the data can be picked up outside the running process, for example by the
visualization notebook below. `data/prices.jsonl` is truncated at the start of every run,
so it always holds only the current run's data points, not a log across multiple runs.
The file is not committed to the repo.

**Scheduling**

A `ScheduledExecutorService` calls the fetch and store logic on a fixed 15 second
interval using `scheduleAtFixedRate`, so requests continue automatically once the
application starts.

**Configuration**

The API key is never hardcoded. It is read once from the `TWELVE_DATA_API_KEY`
environment variable at startup. If the variable is not set, the application prints an
error and exits.

## Running locally

Requires a local JDK 11 or later installation.

From the **project root** (not `src/` — `run.sh` handles moving into `src/` itself):

```
./run.sh
```

This loads `TWELVE_DATA_API_KEY` from `.env`, compiles `src/App.java`, and runs it. The
application keeps polling every 15 seconds until it is stopped, for example with
`Ctrl+C`.

If `./run.sh` doesn't work, check:

- You're in the project root (`ls` should show `run.sh`, not `App.java`).
- It's executable: `chmod +x run.sh`.
- `.env` exists and has `TWELVE_DATA_API_KEY` set (copy it from `.env.example` if not).

**Running the steps by hand instead**, for example to skip `.env`:

```
export TWELVE_DATA_API_KEY=your_api_key_here
cd src
javac App.java
java App
```

`.env` is only a convention for storing the key — nothing loads it automatically when
running these steps by hand, so the key must be exported into the shell before `java`
runs. `run.sh` exists specifically to do that for you.

## Visualizing the data

`DIA_Price_Visualization.ipynb` reads `data/prices.jsonl` and plots price over time with
pandas and matplotlib. It does not install, compile, or run Java — start the tracker
first (above), let it collect a few data points, then run the notebook's cells. Re-run
the cells at any point (the tracker can keep running in the background) to refresh the
chart with newly collected points.
