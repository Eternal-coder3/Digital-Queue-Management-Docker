FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY notification-service/SmartQueue.NotificationService.csproj notification-service/
RUN dotnet restore notification-service/SmartQueue.NotificationService.csproj
COPY notification-service notification-service
RUN dotnet publish notification-service/SmartQueue.NotificationService.csproj -c Release -o /app/publish --no-restore

FROM mcr.microsoft.com/dotnet/aspnet:8.0
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/publish .
USER $APP_UID
EXPOSE 5050
ENTRYPOINT ["dotnet", "SmartQueue.NotificationService.dll"]
