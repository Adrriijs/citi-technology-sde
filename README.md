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
│   └── App.java              Java application source
├── .env.example               Template for the required environment variable
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
`ArrayDeque`, in the order they arrive.

**Scheduling**

A `ScheduledExecutorService` calls the fetch and store logic on a fixed 15 second
interval using `scheduleAtFixedRate`, so requests continue automatically once the
application starts.

**Configuration**

The API key is never hardcoded. It is read once from the `TWELVE_DATA_API_KEY`
environment variable at startup. If the variable is not set, the application prints an
error and exits.

## Running locally

The application is compiled and run directly with a local JDK 11 or later installation.

```
cd src
export TWELVE_DATA_API_KEY=your_api_key_here
javac App.java
java App
```

The application will continue running until it is stopped, for example with `Ctrl+C`.
