using SmartQueue.NotificationService.DTOs;
using SmartQueue.NotificationService.Services;

var builder = WebApplication.CreateBuilder(args);

// Configure Kestrel to run on port 5050
builder.WebHost.ConfigureKestrel(options =>
{
    options.ListenAnyIP(5050);
});

// Bind TwilioSettings from appsettings.json
builder.Services.Configure<TwilioSettings>(builder.Configuration.GetSection("TwilioSettings"));

// Register Services
builder.Services.AddScoped<IEmailService, TwilioEmailService>();

builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();
builder.Services.AddHealthChecks();

// Configure CORS for local development
builder.Services.AddCors(options =>
{
    options.AddPolicy("SmartQueueFrontend", policy =>
    {
        var origins = builder.Configuration["AllowedOrigins"]?.Split(',', StringSplitOptions.RemoveEmptyEntries)
            ?? ["http://localhost:8080"];
        policy.WithOrigins(origins)
              .AllowAnyHeader()
              .AllowAnyMethod();
    });
});

var app = builder.Build();

// Enable Swagger in Development and Production
app.UseSwagger();
app.UseSwaggerUI(c =>
{
    c.SwaggerEndpoint("/swagger/v1/swagger.json", "SmartQueue Notification Service API v1");
    c.RoutePrefix = "swagger";
});

app.UseCors("SmartQueueFrontend");
app.UseAuthorization();
app.MapControllers();
app.MapHealthChecks("/health");

app.Run();
